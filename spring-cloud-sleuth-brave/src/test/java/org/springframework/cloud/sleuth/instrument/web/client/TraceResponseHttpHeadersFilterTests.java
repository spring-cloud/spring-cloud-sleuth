/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.test.TestSpanHandler;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.brave.bridge.BraveSpan;
import org.springframework.cloud.sleuth.brave.bridge.BraveTracer;
import org.springframework.cloud.sleuth.brave.bridge.http.BraveHttpClientHandler;
import org.springframework.cloud.sleuth.brave.bridge.BravePropagator;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

public class TraceResponseHttpHeadersFilterTests {

	StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();

	TestSpanHandler spans = new TestSpanHandler();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(this.currentTraceContext).addSpanHandler(this.spans)
			.build();

	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing).build();

	HttpClientHandler handler = BraveHttpClientHandler.fromBrave(brave.http.HttpClientHandler.create(this.httpTracing));

	@AfterEach
	public void close() {
		this.tracing.close();
		this.currentTraceContext.close();
	}

	@Test
	public void should_not_report_span_when_no_span_was_present_in_attribute() {
		HttpHeadersFilter filter = TraceResponseHttpHeadersFilter.create(BraveTracer.fromBrave(tracing.tracer()),
				this.handler, new BravePropagator(httpTracing.tracing().propagation()));
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set("b3", "52f112af7472aff0-53e6ab6fc5dfee58");
		MockServerHttpRequest request = MockServerHttpRequest.post("foo/bar").headers(httpHeaders).build();
		MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();

		filter.filter(httpHeaders, exchange);

		BDDAssertions.then(this.spans).isEmpty();
	}

	@Test
	public void should_report_span_when_span_was_present_in_attribute() {
		HttpHeadersFilter filter = TraceResponseHttpHeadersFilter.create(BraveTracer.fromBrave(tracing.tracer()),
				this.handler, new BravePropagator(httpTracing.tracing().propagation()));
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set("b3", "52f112af7472aff0-53e6ab6fc5dfee58");
		MockServerHttpRequest request = MockServerHttpRequest.post("foo/bar").headers(httpHeaders).build();
		MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();
		exchange.getAttributes().put(TraceResponseHttpHeadersFilter.SPAN_ATTRIBUTE,
				BraveSpan.fromBrave(this.tracing.tracer().nextSpan()));

		filter.filter(httpHeaders, exchange);

		BDDAssertions.then(this.spans).isNotEmpty();
	}

}
