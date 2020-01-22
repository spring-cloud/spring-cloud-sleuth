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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.List;
import java.util.Map;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

final class TraceRequestHttpHeadersFilter extends AbstractHttpHeadersFilter {

	private static final Log log = LogFactory.getLog(TraceRequestHttpHeadersFilter.class);

	private TraceRequestHttpHeadersFilter(HttpTracing httpTracing) {
		super(httpTracing);
	}

	static HttpHeadersFilter create(HttpTracing httpTracing) {
		return new TraceRequestHttpHeadersFilter(httpTracing);
	}

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		if (log.isDebugEnabled()) {
			log.debug("Will instrument the HTTP request headers ["
					+ exchange.getRequest().getHeaders() + "]");
		}
		HttpClientRequest request = new HttpClientRequest(exchange.getRequest(), input);
		Span currentSpan = currentSpan(request);
		Span span = injectedSpan(request, currentSpan);
		if (log.isDebugEnabled()) {
			log.debug(
					"Client span  " + span + " created for the request. New headers are "
							+ request.filteredHeaders.toSingleValueMap());
		}
		exchange.getAttributes().put(SPAN_ATTRIBUTE, span);
		HttpHeaders headersWithInput = new HttpHeaders();
		headersWithInput.addAll(input);
		addHeadersWithInput(request.filteredHeaders, headersWithInput);
		if (headersWithInput.containsKey("b3") || headersWithInput.containsKey("B3")) {
			headersWithInput.keySet().remove("b3");
			headersWithInput.keySet().remove("B3");
		}
		return headersWithInput;
	}

	private Span currentSpan(HttpClientRequest request) {
		Span currentSpan = this.tracer.currentSpan();
		if (currentSpan != null) {
			return currentSpan;
		}
		// Usually, an HTTP client would not attempt to resume a trace from headers, as a
		// server would always place its span in scope. However, in commit 848442e,
		// this behavior was added in support of gateway.
		TraceContextOrSamplingFlags contextOrFlags = this.extractor.extract(request);
		return this.tracer.nextSpan(contextOrFlags);
	}

	private Span injectedSpan(HttpClientRequest request, Span currentSpan) {
		if (currentSpan == null) {
			return this.handler.handleSend(request);
		}
		Span clientSpan = this.tracer.newChild(currentSpan.context());
		return this.handler.handleSend(request, clientSpan);
	}

	private void addHeadersWithInput(HttpHeaders filteredHeaders,
			HttpHeaders headersWithInput) {
		for (Map.Entry<String, List<String>> entry : filteredHeaders.entrySet()) {
			String key = entry.getKey();
			List<String> value = entry.getValue();
			headersWithInput.put(key, value);
		}
	}

	@Override
	public boolean supports(Type type) {
		return type.equals(Type.REQUEST);
	}

}

final class TraceResponseHttpHeadersFilter extends AbstractHttpHeadersFilter {

	private static final Log log = LogFactory
			.getLog(TraceResponseHttpHeadersFilter.class);

	private TraceResponseHttpHeadersFilter(HttpTracing httpTracing) {
		super(httpTracing);
	}

	static HttpHeadersFilter create(HttpTracing httpTracing) {
		return new TraceResponseHttpHeadersFilter(httpTracing);
	}

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		Object storedSpan = exchange.getAttribute(SPAN_ATTRIBUTE);
		if (storedSpan == null) {
			return input;
		}
		if (log.isDebugEnabled()) {
			log.debug("Will instrument the response");
		}
		HttpClientResponse response = new HttpClientResponse(exchange.getResponse());
		this.handler.handleReceive(response, null, (Span) storedSpan);
		if (log.isDebugEnabled()) {
			log.debug("The response was handled for span " + storedSpan);
		}
		return new HttpHeaders(input);
	}

	@Override
	public boolean supports(Type type) {
		return type.equals(Type.RESPONSE);
	}

}

abstract class AbstractHttpHeadersFilter implements HttpHeadersFilter {

	static final String SPAN_ATTRIBUTE = Span.class.getName();

	final Tracer tracer;

	final HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler;

	final HttpTracing httpTracing;

	final Extractor<HttpClientRequest> extractor;

	AbstractHttpHeadersFilter(HttpTracing httpTracing) {
		this.tracer = httpTracing.tracing().tracer();
		this.extractor = httpTracing.tracing().propagation()
				.extractor(HttpClientRequest::header);
		this.handler = HttpClientHandler.create(httpTracing);
		this.httpTracing = httpTracing;
	}

	static final class HttpClientRequest extends brave.http.HttpClientRequest {

		final ServerHttpRequest delegate;

		final HttpHeaders filteredHeaders;

		HttpClientRequest(ServerHttpRequest delegate, HttpHeaders filteredHeaders) {
			this.delegate = delegate;
			this.filteredHeaders = filteredHeaders;
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public String method() {
			return delegate.getMethodValue();
		}

		@Override
		public String path() {
			return delegate.getURI().getPath();
		}

		@Override
		public String url() {
			return delegate.getURI().toString();
		}

		@Override
		public String header(String name) {
			return filteredHeaders.getFirst(name);
		}

		@Override
		public void header(String name, String value) {
			filteredHeaders.set(name, value);
		}

	}

	static final class HttpClientResponse extends brave.http.HttpClientResponse {

		final ServerHttpResponse delegate;

		HttpClientResponse(ServerHttpResponse delegate) {
			this.delegate = delegate;
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public int statusCode() {
			return delegate.getStatusCode() != null ? delegate.getStatusCode().value()
					: 0;
		}

	}

}
