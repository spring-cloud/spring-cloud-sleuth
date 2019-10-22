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
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.NonNull;
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
			log.debug("Will instrument the HTTP request headers");
		}
		TraceCarrier carrier = new TraceCarrier(exchange.getRequest(), input);
		Span span = this.handler.handleSend(this.injector, carrier);
		if (log.isDebugEnabled()) {
			log.debug("Client span  " + span + " created for the request. New headers are "
					+ carrier.filteredHeaders.toSingleValueMap());
		}
		exchange.getAttributes().put(SPAN_ATTRIBUTE, span);
		HttpHeaders headersWithInput = new HttpHeaders();
		headersWithInput.addAll(input);
		addHeadersWithInput(carrier.filteredHeaders, headersWithInput);
		return headersWithInput;
	}

	private void addHeadersWithInput(HttpHeaders filteredHeaders,
			HttpHeaders headersWithInput) {
		for (Map.Entry<String, List<String>> entry : builder.build().getHeaders()
				.entrySet()) {
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

class TraceCarrier {

	final ServerHttpRequest originalRequest;

	final HttpHeaders filteredHeaders;

	TraceCarrier(@NonNull ServerHttpRequest originalRequest, @NonNull HttpHeaders filteredHeaders) {
		this.originalRequest = originalRequest;
		this.filteredHeaders = filteredHeaders;
	}

}

final class TraceResponseHttpHeadersFilter extends AbstractHttpHeadersFilter {

	private static final Log log = LogFactory.getLog(TraceResponseHttpHeadersFilter.class);

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
		this.handler.handleReceive(exchange.getResponse(), null, (Span) storedSpan);
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

	private static final Propagation.Setter<TraceCarrier, String> SETTER = new Propagation.Setter<TraceCarrier, String>() {
		@Override
		public void put(TraceCarrier carrier, String key, String value) {
			carrier.filteredHeaders.set(key, value);
		}

		@Override
		public String toString() {
			return "TraceCarrier::httpHeaders::set";
		}
	};

	final Tracer tracer;

	final HttpClientHandler<TraceCarrier, ServerHttpResponse> handler;

	final TraceContext.Injector<TraceCarrier> injector;

	final HttpTracing httpTracing;

	AbstractHttpHeadersFilter(HttpTracing httpTracing) {
		this.tracer = httpTracing.tracing().tracer();
		this.handler = HttpClientHandler.create(httpTracing, new ServerHttpAdapter());
		this.injector = httpTracing.tracing().propagation().injector(SETTER);
		this.httpTracing = httpTracing;
	}

	private static class ServerHttpAdapter extends brave.http.HttpClientAdapter<TraceCarrier, ServerHttpResponse> {

		@Override
		public String method(TraceCarrier request) {
			return request.originalRequest.getMethodValue();
		}

		@Override
		public String url(TraceCarrier request) {
			return request.originalRequest.getURI().toString();
		}

		@Override
		public String requestHeader(TraceCarrier request, String name) {
			Object result = request.filteredHeaders.get(name);
			return result != null ? result.toString() : "";
		}

		@Override
		public Integer statusCode(ServerHttpResponse response) {
			return response.getStatusCode() != null ? response.getStatusCode().value() : null;
		}

	}

}
