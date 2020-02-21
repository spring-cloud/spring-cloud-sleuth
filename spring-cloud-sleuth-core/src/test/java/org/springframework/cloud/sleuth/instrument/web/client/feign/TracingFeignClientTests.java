/*
 * Copyright 2013-2019 the original author or authors.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpClientParser;
import brave.http.HttpTracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import feign.Client;
import feign.Request;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@ExtendWith(MockitoExtension.class)
public class TracingFeignClientTests {

	Request request = Request.create("GET", "https://foo", new HashMap<>(), null, null);

	Request.Options options = new Request.Options();

	List<zipkin2.Span> spans = new ArrayList<>();

	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
					.addScopeDecorator(StrictScopeDecorator.create()).build())
			.spanReporter(spans::add).build();

	Tracer tracer = this.tracing.tracer();

	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing)
			.clientParser(new HttpClientParser()).build();

	@Mock
	Client client;

	Client traceFeignClient;

	@BeforeEach
	public void setup() {
		this.traceFeignClient = TracingFeignClient.create(this.httpTracing, this.client);
	}

	@Test
	public void should_log_cr_when_response_successful() throws IOException {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.traceFeignClient.execute(this.request, this.options);
		}
		finally {
			span.finish();
		}

		then(spans.get(0)).extracting("kind.ordinal")
				.isEqualTo(Span.Kind.CLIENT.ordinal());
	}

	@Test
	public void should_log_error_when_exception_thrown() throws IOException {
		Span span = this.tracer.nextSpan().name("foo");
		BDDMockito.given(this.client.execute(BDDMockito.any(), BDDMockito.any()))
				.willThrow(new RuntimeException("exception has occurred"));

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.traceFeignClient.execute(this.request, this.options);
			BDDAssertions.fail("Exception should have been thrown");
		}
		catch (Exception e) {
		}
		finally {
			span.finish();
		}

		then(this.spans.get(0)).extracting("kind.ordinal")
				.isEqualTo(Span.Kind.CLIENT.ordinal());
		then(this.spans.get(0).tags()).containsEntry("error", "exception has occurred");
	}

}
