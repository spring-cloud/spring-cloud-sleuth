/*
 * Copyright 2013-2018 the original author or authors.
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

import org.assertj.core.api.BDDAssertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.spring.web.TracingClientHttpRequestInterceptor;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * @author Marcin Grzejszczak
 */
public class TraceRestTemplateInterceptorIntegrationTests {

	@Rule public final MockWebServer mockWebServer = new MockWebServer();

	private RestTemplate template = new RestTemplate(clientHttpRequestFactory());

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(new StrictCurrentTraceContext())
			.spanReporter(this.reporter)
			.build();
	Tracer tracer = this.tracing.tracer();

	@Before
	public void setup() {
		this.template.setInterceptors(Arrays.<ClientHttpRequestInterceptor>asList(
				TracingClientHttpRequestInterceptor.create(HttpTracing.create(this.tracing))));
	}

	@After
	public void clean() {
		Tracing.current().close();
	}

	// Issue #198
	@Test
	public void spanRemovedFromThreadUponException() throws IOException {
		this.mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
		Span span = this.tracer.nextSpan().name("new trace");

		try(Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.template.getForEntity(
					"http://localhost:" + this.mockWebServer.getPort() + "/exception",
					Map.class).getBody();
			Assert.fail("should throw an exception");
		} catch (RuntimeException e) {
			BDDAssertions.then(e).hasRootCauseInstanceOf(IOException.class);
		} finally {
			span.finish();
		}

		// 1 span "new race", 1 span "rest template"
		BDDAssertions.then(this.reporter.getSpans()).hasSize(2);
		zipkin2.Span span1 = this.reporter.getSpans().get(0);
		BDDAssertions.then(span1.tags())
				.containsEntry("error", "Read timed out");
		BDDAssertions.then(span1.kind().ordinal()).isEqualTo(Span.Kind.CLIENT.ordinal());
	}

	private ClientHttpRequestFactory clientHttpRequestFactory() {
		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		factory.setReadTimeout(100);
		factory.setConnectTimeout(100);
		return factory;
	}

}
