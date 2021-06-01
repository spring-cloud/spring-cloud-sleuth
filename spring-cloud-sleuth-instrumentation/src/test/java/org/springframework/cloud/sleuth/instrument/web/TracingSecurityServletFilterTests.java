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

import java.io.IOException;
import java.util.Collections;

import javax.servlet.ServletException;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.tracer.SimpleSpan;
import org.springframework.cloud.sleuth.tracer.SimpleTracer;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;

import static org.assertj.core.api.BDDAssertions.then;

class TracingSecurityServletFilterTests {

	SimpleTracer tracer = new SimpleTracer();

	TracingSecurityServletFilter filter = new TracingSecurityServletFilter(this.tracer) {
		@Override
		SecurityContext getContext() {
			return new SecurityContextImpl(new TestingAuthenticationToken(
					new User("foo", "bar", Collections.singletonList(new SimpleGrantedAuthority("my-role"))), null,
					"my-authority"));
		}
	};

	@Test
	void should_tag_current_span_with_security_info() throws ServletException, IOException {
		SimpleSpan simpleSpan = this.tracer.nextSpan().start();

		this.filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

		then(simpleSpan.tags).containsEntry("security.authentication.authorities", "my-authority")
				.containsEntry("security.authentication.authenticated", "true")
				.containsEntry("security.principal.enabled", "true")
				.containsEntry("security.principal.authorities", "my-role")
				.containsEntry("security.principal.account-non-expired", "true")
				.containsEntry("security.principal.credentials-non-expired", "true");
	}

	@Test
	void should_do_nothing_when_there_is_no_current_span() throws ServletException, IOException {
		this.filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

		then(this.tracer.spans).isEmpty();
	}

}
