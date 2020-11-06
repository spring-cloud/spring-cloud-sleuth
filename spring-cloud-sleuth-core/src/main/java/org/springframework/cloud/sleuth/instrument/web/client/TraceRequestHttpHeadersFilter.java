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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpClientRequest;
import org.springframework.cloud.sleuth.api.http.HttpClientResponse;
import org.springframework.cloud.sleuth.api.propagation.Propagator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

final class TraceRequestHttpHeadersFilter extends AbstractHttpHeadersFilter {

	private static final Log log = LogFactory.getLog(TraceRequestHttpHeadersFilter.class);

	static final String TRACE_REQUEST_ATTR = TraceContext.class.getName();

	private TraceRequestHttpHeadersFilter(Tracer tracer, HttpClientHandler handler, Propagator propagator) {
		super(tracer, handler, propagator);
	}

	static HttpHeadersFilter create(Tracer tracer, HttpClientHandler handler, Propagator propagator) {
		return new TraceRequestHttpHeadersFilter(tracer, handler, propagator);
	}

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		if (log.isDebugEnabled()) {
			log.debug("Will instrument the HTTP request headers [" + exchange.getRequest().getHeaders() + "]");
		}
		ServerHttpClientRequest request = new ServerHttpClientRequest(exchange.getRequest(), input);
		Span currentSpan = currentSpan(exchange, request);
		Span span = injectedSpan(request, currentSpan);
		if (log.isDebugEnabled()) {
			log.debug("Client span  " + span + " created for the request. New headers are "
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

	private Span currentSpan(ServerWebExchange exchange, ServerHttpClientRequest request) {
		Span currentSpan = currentSpan(exchange);
		if (currentSpan != null) {
			return currentSpan;
		}
		// Usually, an HTTP client would not attempt to resume a trace from headers, as a
		// server would always place its span in scope. However, in commit 848442e,
		// this behavior was added in support of gateway.
		return this.propagator.extract(request, HttpClientRequest::header).start();
	}

	private Span currentSpan(ServerWebExchange exchange) {
		Object attribute = exchange.getAttribute(TRACE_REQUEST_ATTR);
		if (attribute instanceof Span) {
			if (log.isDebugEnabled()) {
				log.debug("Found trace request attribute in the server web exchange [" + attribute + "]");
			}
			return (Span) attribute;
		}
		return this.tracer.currentSpan();
	}

	private Span injectedSpan(ServerHttpClientRequest request, Span currentSpan) {
		if (currentSpan == null) {
			return this.handler.handleSend(request);
		}
		try (Tracer.SpanInScope ws = this.tracer.withSpan(currentSpan)) {
			Span clientSpan = this.tracer.nextSpan();
			return this.handler.handleSend(request, clientSpan.context());
		}
	}

	private void addHeadersWithInput(HttpHeaders filteredHeaders, HttpHeaders headersWithInput) {
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

	private static final Log log = LogFactory.getLog(TraceResponseHttpHeadersFilter.class);

	private TraceResponseHttpHeadersFilter(Tracer tracer, HttpClientHandler handler, Propagator propagator) {
		super(tracer, handler, propagator);
	}

	static HttpHeadersFilter create(Tracer tracer, HttpClientHandler handler, Propagator propagator) {
		return new TraceResponseHttpHeadersFilter(tracer, handler, propagator);
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
		ServerHttpClientResponse response = new ServerHttpClientResponse(exchange.getResponse());
		this.handler.handleReceive(response, (Span) storedSpan);
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

	final HttpClientHandler handler;

	final Propagator propagator;

	AbstractHttpHeadersFilter(Tracer tracer, HttpClientHandler handler, Propagator propagator) {
		this.tracer = tracer;
		this.propagator = propagator;
		this.handler = handler;
	}

	static final class ServerHttpClientRequest implements HttpClientRequest {

		final ServerHttpRequest delegate;

		final HttpHeaders filteredHeaders;

		ServerHttpClientRequest(ServerHttpRequest delegate, HttpHeaders filteredHeaders) {
			this.delegate = delegate;
			this.filteredHeaders = filteredHeaders;
		}

		@Override
		public Collection<String> headerNames() {
			return this.delegate.getHeaders().keySet();
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

	static final class ServerHttpClientResponse implements HttpClientResponse {

		final ServerHttpResponse delegate;

		ServerHttpClientResponse(ServerHttpResponse delegate) {
			this.delegate = delegate;
		}

		@Override
		public Collection<String> headerNames() {
			return this.delegate.getHeaders().keySet();
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public int statusCode() {
			return delegate.getStatusCode() != null ? delegate.getStatusCode().value() : 0;
		}

		@Override
		public String header(String header) {
			List<String> headers = delegate.getHeaders().get(header);
			if (headers == null || headers.isEmpty()) {
				return null;
			}
			return headers.get(0);
		}

	}

}
