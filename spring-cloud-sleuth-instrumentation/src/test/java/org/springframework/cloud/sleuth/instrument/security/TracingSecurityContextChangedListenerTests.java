/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.security;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextChangedEvent;
import org.springframework.security.core.context.SecurityContextImpl;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Jonatan Ivanov
 */
@ExtendWith(MockitoExtension.class)
class TracingSecurityContextChangedListenerTests {

	@Mock
	private Tracer tracer;

	@Mock
	private Span span;

	@Captor
	private ArgumentCaptor<String> eventCaptor;

	@InjectMocks
	TracingSecurityContextChangedListener listener;

	@Test
	void null_authentication_objects_should_result_in_noop() {
		listener.securityContextChanged(new SecurityContextChangedEvent(null, null));
		listener.securityContextChanged(new SecurityContextChangedEvent(new SecurityContextImpl(null), null));
		listener.securityContextChanged(new SecurityContextChangedEvent(null, new SecurityContextImpl(null)));
		listener.securityContextChanged(
				new SecurityContextChangedEvent(new SecurityContextImpl(null), new SecurityContextImpl(null)));

		verifyNoInteractions(tracer);
	}

	@Test
	void null_span_should_result_in_noop() {
		when(tracer.currentSpan()).thenReturn(null);
		listener.securityContextChanged(new SecurityContextChangedEvent(null, fakeSecurityContext()));

		verify(tracer).currentSpan();
	}

	@Test
	void set_context_changed_event_should_result_in_new_even_on_the_span() {
		when(tracer.currentSpan()).thenReturn(span);
		listener.securityContextChanged(new SecurityContextChangedEvent(null, fakeSecurityContext()));

		verify(tracer).currentSpan();
		verify(span).event(eventCaptor.capture());

		then(eventCaptor.getValue()).isEqualTo("Authentication set AnonymousAuthenticationToken[ANONYMOUS]");
	}

	@Test
	void clear_context_changed_event_should_result_in_new_even_on_the_span() {
		when(tracer.currentSpan()).thenReturn(span);
		listener.securityContextChanged(new SecurityContextChangedEvent(fakeSecurityContext(), null));

		verify(tracer).currentSpan();
		verify(span).event(eventCaptor.capture());

		then(eventCaptor.getValue()).isEqualTo("Authentication cleared AnonymousAuthenticationToken[ANONYMOUS]");
	}

	@Test
	void replace_context_changed_event_should_result_in_new_even_on_the_span() {
		when(tracer.currentSpan()).thenReturn(span);
		listener.securityContextChanged(new SecurityContextChangedEvent(fakeSecurityContext(), fakeSecurityContext()));

		verify(tracer).currentSpan();
		verify(span).event(eventCaptor.capture());

		then(eventCaptor.getValue()).isEqualTo(
				"Authentication replaced AnonymousAuthenticationToken[ANONYMOUS] -> AnonymousAuthenticationToken[ANONYMOUS]");
	}

	private SecurityContext fakeSecurityContext() {
		return new SecurityContextImpl(fakeAuthentication());
	}

	private Authentication fakeAuthentication() {
		return new AnonymousAuthenticationToken("123", "username",
				Collections.singletonList(new SimpleGrantedAuthority("ANONYMOUS")));
	}

}
