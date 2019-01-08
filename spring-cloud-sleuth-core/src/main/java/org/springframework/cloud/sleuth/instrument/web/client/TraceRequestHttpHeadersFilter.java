/*
 * Copyright 2013-2019 the original author or authors.
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

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

class TraceRequestHttpHeadersFilter extends AbstractHttpHeadersFilter {

	static HttpHeadersFilter create(HttpTracing httpTracing) {
		return new TraceRequestHttpHeadersFilter(httpTracing);
	}

	private TraceRequestHttpHeadersFilter(HttpTracing httpTracing) {
		super(httpTracing);
	}

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		Object storedSpan = exchange.getAttribute(SPAN_ATTRIBUTE);
		Span span = clientSent(exchange, storedSpan);
		exchange.getAttributes().put(SPAN_ATTRIBUTE, span);
		return new HttpHeaders(exchange.getRequest().getHeaders());
	}

	private Span clientSent(ServerWebExchange exchange, Object storedSpan) {
		if (storedSpan != null) {
			return this.handler.handleSend(this.injector, exchange.getRequest(),
					(Span) storedSpan);
		}
		return this.handler.handleSend(this.injector, exchange.getRequest());
	}

	@Override
	public boolean supports(Type type) {
		return type.equals(Type.REQUEST);
	}

}

class TraceResponseHttpHeadersFilter extends AbstractHttpHeadersFilter {

	static HttpHeadersFilter create(HttpTracing httpTracing) {
		return new TraceResponseHttpHeadersFilter(httpTracing);
	}

	private TraceResponseHttpHeadersFilter(HttpTracing httpTracing) {
		super(httpTracing);
	}

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		Object storedSpan = exchange.getAttribute(SPAN_ATTRIBUTE);
		if (storedSpan == null) {
			return input;
		}
		this.handler.handleReceive(exchange.getResponse(), null, (Span) storedSpan);
		return new HttpHeaders(input);
	}

	@Override
	public boolean supports(Type type) {
		return type.equals(Type.RESPONSE);
	}

}

abstract class AbstractHttpHeadersFilter implements HttpHeadersFilter {

	static final String SPAN_ATTRIBUTE = Span.class.getName();

	private static final Propagation.Setter<ServerHttpRequest, String> SETTER = new Propagation.Setter<ServerHttpRequest, String>() {
		@Override
		public void put(ServerHttpRequest carrier, String key, String value) {
			if (!carrier.getHeaders().containsKey(key)) {
				carrier.getHeaders().add(key, value);
			}
		}

		@Override
		public String toString() {
			return "ServerHttpRequest::HttpHeaders::add";
		}
	};

	final Tracer tracer;

	final HttpClientHandler<ServerHttpRequest, ServerHttpResponse> handler;

	final TraceContext.Injector<ServerHttpRequest> injector;

	final HttpTracing httpTracing;

	AbstractHttpHeadersFilter(HttpTracing httpTracing) {
		this.tracer = httpTracing.tracing().tracer();
		this.handler = HttpClientHandler.create(httpTracing, new ServerHttpAdapter());
		this.injector = httpTracing.tracing().propagation().injector(SETTER);
		this.httpTracing = httpTracing;
	}

	private static class ServerHttpAdapter
			extends brave.http.HttpClientAdapter<ServerHttpRequest, ServerHttpResponse> {

		@Override
		public String method(ServerHttpRequest request) {
			return request.getMethodValue();
		}

		@Override
		public String url(ServerHttpRequest request) {
			return request.getURI().toString();
		}

		@Override
		public String requestHeader(ServerHttpRequest request, String name) {
			Object result = request.getHeaders().get(name);
			return result != null ? result.toString() : "";
		}

		@Override
		public Integer statusCode(ServerHttpResponse response) {
			return response.getStatusCode() != null ? response.getStatusCode().value()
					: null;
		}

	}

}