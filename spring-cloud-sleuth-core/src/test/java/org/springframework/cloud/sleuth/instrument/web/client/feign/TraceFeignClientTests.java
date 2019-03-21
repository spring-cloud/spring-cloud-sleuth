/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import feign.Client;
import feign.Request;
import feign.Response;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.ExceptionMessageErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.SleuthAssertions;
import org.springframework.cloud.sleuth.instrument.web.HttpSpanInjector;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.instrument.web.ZipkinHttpSpanInjector;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceFeignClientTests {

	ArrayListSpanAccumulator spanAccumulator = new ArrayListSpanAccumulator();
	@Mock BeanFactory beanFactory;
	Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(), new DefaultSpanNamer(),
			new NoOpSpanLogger(), this.spanAccumulator, new TraceKeys());
	@Mock Client client;
	@InjectMocks TraceFeignClient traceFeignClient;

	@Before
	@After
	public void setup() {
		TestSpanContextHolder.removeCurrentSpan();
		ExceptionUtils.setFail(true);
		BDDMockito.given(this.beanFactory.getBean(HttpTraceKeysInjector.class))
				.willReturn(new HttpTraceKeysInjector(this.tracer, new TraceKeys()));
		BDDMockito.given(this.beanFactory.getBean(HttpSpanInjector.class))
				.willReturn(new ZipkinHttpSpanInjector());
		BDDMockito.given(this.beanFactory.getBean(Tracer.class)).willReturn(this.tracer);
		BDDMockito.given(this.beanFactory.getBean(ErrorParser.class)).willReturn(new ExceptionMessageErrorParser());
	}

	@Test
	public void should_log_cr_when_response_successful() throws IOException {
		Span span = this.tracer.createSpan("foo");
		Response response = this.traceFeignClient.execute(
				Request.create("GET", "http://foo", new HashMap<>(), "".getBytes(),
						Charset.defaultCharset()), new Request.Options());

		then(this.tracer.getCurrentSpan()).isEqualTo(span);
		then(this.spanAccumulator.getSpans().get(0)).hasLoggedAnEvent(Span.CLIENT_RECV);
	}

	@Test
	public void should_log_error_when_exception_thrown() throws IOException {
		Span span = this.tracer.createSpan("foo");
		BDDMockito.given(this.client.execute(BDDMockito.any(), BDDMockito.any()))
				.willThrow(new RuntimeException("exception has occurred"));

		try {
			this.traceFeignClient.execute(
					Request.create("GET", "http://foo", new HashMap<>(), "".getBytes(),
							Charset.defaultCharset()), new Request.Options());
			SleuthAssertions.fail("Exception should have been thrown");
		} catch (Exception e) {}

		then(this.tracer.getCurrentSpan()).isEqualTo(span);
		then(this.spanAccumulator.getSpans().get(0))
				.hasATag(Span.SPAN_ERROR_TAG_NAME, "exception has occurred");
	}

	@Test
	public void should_shorten_the_span_name() throws IOException {
		this.traceFeignClient.execute(
				Request.create("GET", "http://foo/" + bigName(), new HashMap<>(), "".getBytes(),
						Charset.defaultCharset()), new Request.Options());

		then(this.spanAccumulator.getSpans().get(0).getName()).hasSize(50);
	}

	private String bigName() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			sb.append("a");
		}
		return sb.toString();
	}

}