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

package org.springframework.cloud.sleuth.instrument.web;

import reactor.core.publisher.Mono;

import org.springframework.cloud.sleuth.Span;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/**
 * A filter that adds security related tags.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TracingSecurityWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		// @formatter:off
		return getContext()
				.filter((c) -> c.getAuthentication() != null)
				.map(SecurityContext::getAuthentication)
				.doOnNext(authentication -> {
					Object attribute = exchange.getAttribute(TraceWebFilter.TRACE_REQUEST_ATTR);
					if (attribute instanceof Span) {
						TracingSecurityTagSetter.setSecurityTags((Span) attribute, authentication);
					}
				})
				.then(chain.filter(exchange));
		// @formatter:on
	}

	Mono<SecurityContext> getContext() {
		return ReactiveSecurityContextHolder.getContext();
	}

}
