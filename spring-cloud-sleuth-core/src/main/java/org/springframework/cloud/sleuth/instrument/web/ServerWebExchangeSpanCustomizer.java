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

package org.springframework.cloud.sleuth.instrument.web;

import brave.Span;
import brave.SpanCustomizer;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.sleuth.instrument.web.TraceWebFilter.TRACE_REQUEST_ATTR;

/**
 * A {@link SpanCustomizer} that operates on {@link ServerWebExchange}'s that have had a {@link Span} inserted by a {@link TraceWebFilter}.
 *
 * @author Andrew Fitzgerald
 */
public class ServerWebExchangeSpanCustomizer implements SpanCustomizer {
	private final ServerWebExchange exchange;

	public ServerWebExchangeSpanCustomizer(ServerWebExchange exchange) {
		Assert.notNull(getSpanFromAttribute(exchange),
				"Expected to find Span in exchange attributes at " + TRACE_REQUEST_ATTR);
		this.exchange = exchange;
	}

	public static ServerWebExchangeSpanCustomizer customizer(ServerWebExchange exchange) {
		return new ServerWebExchangeSpanCustomizer(exchange);
	}

	private Span getSpanFromAttribute(ServerWebExchange exchange) {
		return exchange.getAttribute(TRACE_REQUEST_ATTR);
	}

	@Override
	public SpanCustomizer name(String name) {
		return getSpanFromAttribute(exchange).name(name);
	}

	@Override
	public SpanCustomizer tag(String key, String value) {
		return getSpanFromAttribute(exchange).tag(key, value);
	}

	@Override
	public SpanCustomizer annotate(String value) {
		return getSpanFromAttribute(exchange).annotate(value);
	}
}
