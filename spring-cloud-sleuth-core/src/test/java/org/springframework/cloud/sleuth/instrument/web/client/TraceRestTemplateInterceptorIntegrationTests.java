/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.assertions.SleuthAssertions;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * @author Marcin Grzejszczak
 */
public class TraceRestTemplateInterceptorIntegrationTests {

	@Rule public final MockWebServer mockWebServer = new MockWebServer();

	private RestTemplate template = new RestTemplate(clientHttpRequestFactory());

	private DefaultTracer tracer;

	private StaticApplicationContext publisher = new StaticApplicationContext();

	@Before
	public void setup() {
		this.publisher.refresh();
		this.tracer = new DefaultTracer(new AlwaysSampler(), new Random(), this.publisher,
				new DefaultSpanNamer());
		this.template.setInterceptors(Arrays.<ClientHttpRequestInterceptor>asList(
				new TraceRestTemplateInterceptor(this.tracer)));
		TestSpanContextHolder.removeCurrentSpan();
	}

	@After
	public void clean() throws IOException {
		TestSpanContextHolder.removeCurrentSpan();
	}

	// issue #198
	@Test
	public void spanRemovedFromThreadUponException() throws IOException {
		this.mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
		Span span = this.tracer.createSpan("new trace");

		try {
			this.template.getForEntity(
					"http://localhost:" + this.mockWebServer.getPort() + "/exception",
					Map.class).getBody();
			Assert.fail("should throw an exception");
		} catch (RuntimeException e) {
			SleuthAssertions.then(e).hasRootCauseInstanceOf(IOException.class);
		}

		SleuthAssertions.then(this.tracer.getCurrentSpan()).isEqualTo(span);
		this.tracer.close(span);
	}

	private ClientHttpRequestFactory clientHttpRequestFactory() {
		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		factory.setReadTimeout(100);
		factory.setConnectTimeout(100);
		return factory;
	}

}
