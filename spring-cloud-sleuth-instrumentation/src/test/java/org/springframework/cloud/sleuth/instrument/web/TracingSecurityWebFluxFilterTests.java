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

import java.time.Duration;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.cloud.sleuth.tracer.SimpleSpan;
import org.springframework.cloud.sleuth.tracer.SimpleTracer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;

import static org.assertj.core.api.BDDAssertions.then;

class TracingSecurityWebFluxFilterTests {

	SimpleTracer tracer = new SimpleTracer();

	TracingSecurityWebFilter filter = new TracingSecurityWebFilter() {
		@Override
		Mono<SecurityContext> getContext() {
			return Mono.just(new SecurityContextImpl(new TestingAuthenticationToken(
					new User("foo", "bar", Collections.singletonList(new SimpleGrantedAuthority("my-role"))), null,
					"my-authority")));
		}
	};

	@Test
	void should_tag_current_span_with_security_info() {
		MockServerHttpRequest request = MockServerHttpRequest.post("foo/bar").build();
		MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();
		SimpleSpan simpleSpan = this.tracer.nextSpan().start();
		exchange.getAttributes().put(TraceWebFilter.TRACE_REQUEST_ATTR, simpleSpan);

		this.filter.filter(exchange, exchange1 -> Mono.empty()).block(Duration.ofMillis(10));

		then(simpleSpan.tags).containsEntry("security.authentication.authorities", "my-authority")
				.containsEntry("security.authentication.authenticated", "true")
				.containsEntry("security.principal.enabled", "true")
				.containsEntry("security.principal.authorities", "my-role")
				.containsEntry("security.principal.account-non-expired", "true")
				.containsEntry("security.principal.credentials-non-expired", "true");
	}

	@Test
	void should_do_nothing_when_there_is_no_current_span() {
		MockServerHttpRequest request = MockServerHttpRequest.post("foo/bar").build();
		MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();

		this.filter.filter(exchange, exchange1 -> Mono.empty()).block(Duration.ofMillis(10));

		then(this.tracer.spans).isEmpty();
	}

}
