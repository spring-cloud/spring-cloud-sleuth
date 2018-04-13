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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import feign.Client;
import feign.Request;
import org.assertj.core.api.BDDAssertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.instrument.web.SleuthHttpParserAccessor;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TracingFeignClientTests {

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	@Mock BeanFactory beanFactory;
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(new StrictCurrentTraceContext())
			.spanReporter(this.reporter)
			.build();
	Tracer tracer = this.tracing.tracer();
	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing)
			.clientParser(SleuthHttpParserAccessor.getClient())
			.build();
	@Mock Client client;
	Client traceFeignClient;

	@Before
	public void setup() {
		this.traceFeignClient = TracingFeignClient.create(this.httpTracing, this.client);
	}

	@Test
	public void should_log_cr_when_response_successful() throws IOException {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.traceFeignClient.execute(
					Request.create("GET", "http://foo", new HashMap<>(), "".getBytes(),
							Charset.defaultCharset()), new Request.Options());
		} finally {
			span.finish();
		}

		then(this.reporter.getSpans().get(0)).extracting("kind.ordinal")
				.contains(Span.Kind.CLIENT.ordinal());
	}

	@Test
	public void should_log_error_when_exception_thrown() throws IOException {
		Span span = this.tracer.nextSpan().name("foo");
		BDDMockito.given(this.client.execute(BDDMockito.any(), BDDMockito.any()))
				.willThrow(new RuntimeException("exception has occurred"));

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.traceFeignClient.execute(
					Request.create("GET", "http://foo", new HashMap<>(), "".getBytes(),
							Charset.defaultCharset()), new Request.Options());
			BDDAssertions.fail("Exception should have been thrown");
		} catch (Exception e) {
		} finally {
			span.finish();
		}

		then(this.reporter.getSpans().get(0)).extracting("kind.ordinal")
				.contains(Span.Kind.CLIENT.ordinal());
		then(this.reporter.getSpans().get(0).tags())
				.containsEntry("error", "exception has occurred");
	}

	@Test
	public void should_shorten_the_span_name() throws IOException {
		this.traceFeignClient.execute(
				Request.create("GET", "http://foo/" + bigName(), new HashMap<>(), "".getBytes(),
						Charset.defaultCharset()), new Request.Options());

		then(this.reporter.getSpans().get(0).name()).hasSize(50);
	}

	private String bigName() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			sb.append("a");
		}
		return sb.toString();
	}

}