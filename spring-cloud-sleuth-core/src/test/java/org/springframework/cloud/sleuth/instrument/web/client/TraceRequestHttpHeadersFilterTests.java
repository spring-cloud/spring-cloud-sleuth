package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.Collections;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import org.assertj.core.api.BDDAssertions;
import org.junit.Test;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

public class TraceRequestHttpHeadersFilterTests {

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();

	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
					.addScopeDecorator(StrictScopeDecorator.create()).build())
			.spanReporter(this.reporter).build();

	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing).build();

	@Test
	public void should_override_span_tracing_headers() {
		HttpHeadersFilter filter = TraceRequestHttpHeadersFilter.create(this.httpTracing);
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set("X-Hello", "World");
		httpHeaders.set("X-Auth-User", "aaaa");
		httpHeaders.set("X-B3-TraceId", "52f112af7472aff0");
		httpHeaders.set("X-B3-SpanId", "53e6ab6fc5dfee58");
		MockServerHttpRequest request = MockServerHttpRequest.post("foo/bar")
				.headers(httpHeaders).build();
		MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();

		HttpHeaders filteredHeaders = filter.filter(requestHeaders(), exchange);

		BDDAssertions.then(filteredHeaders.get("X-B3-TraceId"))
				.isNotEqualTo(httpHeaders.get("X-B3-TraceId"));
		BDDAssertions.then(filteredHeaders.get("X-B3-SpanId"))
				.isNotEqualTo(httpHeaders.get("X-B3-SpanId"));
		BDDAssertions.then(filteredHeaders.get("X-Hello"))
				.isEqualTo(Collections.singletonList("World"));
		BDDAssertions.then(filteredHeaders.get("X-Hello-Request"))
				.isEqualTo(Collections.singletonList("Request World"));
		BDDAssertions.then(filteredHeaders.get("X-Auth-User"))
				.hasSize(1);
		BDDAssertions
				.then((Object) exchange
						.getAttribute(TraceRequestHttpHeadersFilter.SPAN_ATTRIBUTE))
				.isNotNull();
	}

	@Test
	public void should_set_tracing_headers() {
		HttpHeadersFilter filter = TraceRequestHttpHeadersFilter.create(this.httpTracing);
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set("X-Hello", "World");
		MockServerHttpRequest request = MockServerHttpRequest.post("foo/bar")
				.headers(httpHeaders).build();
		MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();

		HttpHeaders filteredHeaders = filter.filter(requestHeaders(), exchange);

		BDDAssertions.then(filteredHeaders.get("X-B3-TraceId")).isNotEmpty();
		BDDAssertions.then(filteredHeaders.get("X-B3-SpanId")).isNotEmpty();
		BDDAssertions.then(filteredHeaders.get("X-Hello"))
				.isEqualTo(Collections.singletonList("World"));
		BDDAssertions.then(filteredHeaders.get("X-Hello-Request"))
				.isEqualTo(Collections.singletonList("Request World"));
		BDDAssertions
				.then((Object) exchange
						.getAttribute(TraceRequestHttpHeadersFilter.SPAN_ATTRIBUTE))
				.isNotNull();
	}

	private HttpHeaders requestHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("X-Hello-Request", "Request World");
		headers.add("X-Auth-User", "aaaa");
		return headers;
	}

}