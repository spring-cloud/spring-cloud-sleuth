/*
 * Copyright 2013-2021 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * Trace representation of {@link HttpHeadersFilter} for a response.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class TraceResponseHttpHeadersFilter extends AbstractHttpHeadersFilter {

	private static final Log log = LogFactory.getLog(TraceResponseHttpHeadersFilter.class);

	public TraceResponseHttpHeadersFilter(Tracer tracer, HttpClientHandler handler, Propagator propagator) {
		super(tracer, handler, propagator);
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
