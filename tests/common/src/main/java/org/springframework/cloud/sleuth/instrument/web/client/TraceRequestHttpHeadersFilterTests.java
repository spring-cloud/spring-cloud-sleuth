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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

// This test uses B3 multi format as it is the default for client propagation
public abstract class TraceRequestHttpHeadersFilterTests implements TestTracingAwareSupplier {

	@Test
	public void should_override_span_tracing_headers() {
		HttpHeadersFilter filter = TraceRequestHttpHeadersFilter.create(tracerTest().tracing().tracer(),
				tracerTest().tracing().httpClientHandler(), tracerTest().tracing().propagator());
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set("X-Hello", "World");
		httpHeaders.set("X-B3-TraceId", "52f112af7472aff0");
		httpHeaders.set("X-B3-SpanId", "53e6ab6fc5dfee58");
		MockServerHttpRequest request = MockServerHttpRequest.post("foo/bar").headers(httpHeaders).build();
		MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();

		HttpHeaders filteredHeaders = filter.filter(requestHeaders(httpHeaders), exchange);

		// we want to continue the trace
		BDDAssertions.then(high(filteredHeaders.get("X-B3-TraceId"))).isEqualTo(high(httpHeaders.get("X-B3-TraceId")));
		// but we want to have a new span id
		BDDAssertions.then(high(filteredHeaders.get("X-B3-SpanId"))).isNotEqualTo(high(httpHeaders.get("X-B3-SpanId")));
		BDDAssertions.then(filteredHeaders.get("X-Hello")).isEqualTo(Collections.singletonList("World"));
		BDDAssertions.then(filteredHeaders.get("X-Hello-Request"))
				.isEqualTo(Collections.singletonList("Request World"));
		BDDAssertions.then(filteredHeaders.get("X-Auth-User")).hasSize(1);
		BDDAssertions.then((Object) exchange.getAttribute(TraceRequestHttpHeadersFilter.SPAN_ATTRIBUTE)).isNotNull();
	}

	@Test
	public void should_override_span_tracing_headers_when_using_b3() {
		HttpHeadersFilter filter = TraceRequestHttpHeadersFilter.create(tracerTest().tracing().tracer(),
				tracerTest().tracing().httpClientHandler(), tracerTest().tracing().propagator());
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set("X-Hello", "World");
		httpHeaders.set("B3", "1111111111111111-1111111111111111");
		MockServerHttpRequest request = MockServerHttpRequest.post("foo/bar").headers(httpHeaders).build();
		MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();

		HttpHeaders filteredHeaders = filter.filter(requestHeaders(httpHeaders), exchange);

		// we want to continue the trace
		BDDAssertions.then(high(filteredHeaders.get("X-B3-TraceId")))
				.isEqualTo(high(Collections.singletonList("1111111111111111")));
		// but we want to have a new span id
		BDDAssertions.then(high(filteredHeaders.get("X-B3-SpanId")))
				.isNotEqualTo(high(Collections.singletonList("1111111111111111")));
		// we don't want to propagate b3
		BDDAssertions.then(filteredHeaders.get("B3")).isNullOrEmpty();
		BDDAssertions.then(filteredHeaders.get("X-Hello")).isEqualTo(Collections.singletonList("World"));
		BDDAssertions.then(filteredHeaders.get("X-Hello-Request"))
				.isEqualTo(Collections.singletonList("Request World"));
		BDDAssertions.then(filteredHeaders.get("X-Auth-User")).hasSize(1);
		BDDAssertions.then((Object) exchange.getAttribute(TraceRequestHttpHeadersFilter.SPAN_ATTRIBUTE)).isNotNull();
	}

	@Test
	public void should_set_tracing_headers() {
		HttpHeadersFilter filter = TraceRequestHttpHeadersFilter.create(tracerTest().tracing().tracer(),
				tracerTest().tracing().httpClientHandler(), tracerTest().tracing().propagator());
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set("X-Hello", "World");
		MockServerHttpRequest request = MockServerHttpRequest.post("foo/bar").headers(httpHeaders).build();
		MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();

		HttpHeaders filteredHeaders = filter.filter(requestHeaders(httpHeaders), exchange);

		BDDAssertions.then(filteredHeaders.get("X-B3-TraceId")).isNotEmpty();
		BDDAssertions.then(filteredHeaders.get("X-B3-SpanId")).isNotEmpty();
		BDDAssertions.then(filteredHeaders.get("X-Hello")).isEqualTo(Collections.singletonList("World"));
		BDDAssertions.then(filteredHeaders.get("X-Hello-Request"))
				.isEqualTo(Collections.singletonList("Request World"));
		BDDAssertions.then((Object) exchange.getAttribute(TraceRequestHttpHeadersFilter.SPAN_ATTRIBUTE)).isNotNull();
	}

	// #1469
	@Test
	public void should_reuse_headers_only_from_input_since_exchange_may_contain_already_ignored_headers() {
		HttpHeadersFilter filter = TraceRequestHttpHeadersFilter.create(tracerTest().tracing().tracer(),
				tracerTest().tracing().httpClientHandler(), tracerTest().tracing().propagator());
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set("X-Hello", "World");
		MockServerHttpRequest request = MockServerHttpRequest.post("foo/bar").headers(httpHeaders).build();
		MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();

		HttpHeaders filteredHeaders = filter.filter(requestHeaders(), exchange);

		BDDAssertions.then(filteredHeaders.get("X-B3-TraceId")).isNotEmpty();
		BDDAssertions.then(filteredHeaders.get("X-B3-SpanId")).isNotEmpty();
		BDDAssertions.then(filteredHeaders.get("X-Hello")).isNullOrEmpty();
		BDDAssertions.then((Object) exchange.getAttribute(TraceRequestHttpHeadersFilter.SPAN_ATTRIBUTE)).isNotNull();
	}

	// #1352
	@Test
	public void should_set_tracing_headers_with_multiple_values() {
		HttpHeadersFilter filter = TraceRequestHttpHeadersFilter.create(tracerTest().tracing().tracer(),
				tracerTest().tracing().httpClientHandler(), tracerTest().tracing().propagator());
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add("X-Hello-Request", "Request World");
		httpHeaders.addAll("X-Hello", Arrays.asList("World1", "World2"));
		MockServerHttpRequest request = MockServerHttpRequest.post("foo/bar").headers(httpHeaders).build();
		MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();

		HttpHeaders filteredHeaders = filter.filter(httpHeaders, exchange);

		BDDAssertions.then(filteredHeaders.get("X-B3-TraceId")).isNotEmpty();
		BDDAssertions.then(filteredHeaders.get("X-B3-SpanId")).isNotEmpty();
		BDDAssertions.then(filteredHeaders.get("X-Hello")).isEqualTo(Arrays.asList("World1", "World2"));
		BDDAssertions.then(filteredHeaders.get("X-Hello-Request"))
				.isEqualTo(Collections.singletonList("Request World"));
		BDDAssertions.then((Object) exchange.getAttribute(TraceRequestHttpHeadersFilter.SPAN_ATTRIBUTE)).isNotNull();
	}

	private HttpHeaders requestHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("X-Hello-Request", "Request World");
		headers.add("X-Auth-User", "aaaa");
		return headers;
	}

	private HttpHeaders requestHeaders(HttpHeaders originalHeaders) {
		HttpHeaders headers = new HttpHeaders();
		headers.putAll(originalHeaders);
		headers.add("X-Hello-Request", "Request World");
		headers.add("X-Auth-User", "aaaa");
		return headers;
	}

	private String high(List<String> ids) {
		BDDAssertions.then(ids).isNotNull().isNotEmpty();
		String id = ids.get(0);
		if (id.length() == 32) {
			return id.substring(16);
		}
		return id;
	}

}
