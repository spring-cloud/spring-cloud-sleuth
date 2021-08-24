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
		listener.securityContextChanged(new SecurityContextChangedEvent(new SecurityContextImpl(null), new SecurityContextImpl(null)));

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

		then(eventCaptor.getValue()).isEqualTo("Authentication replaced AnonymousAuthenticationToken[ANONYMOUS] -> AnonymousAuthenticationToken[ANONYMOUS]");
	}

	private SecurityContext fakeSecurityContext() {
		return new SecurityContextImpl(fakeAuthentication());
	}

	private Authentication fakeAuthentication() {
		return new AnonymousAuthenticationToken("123", "username", Collections.singletonList(new SimpleGrantedAuthority("ANONYMOUS")));
	}
}
