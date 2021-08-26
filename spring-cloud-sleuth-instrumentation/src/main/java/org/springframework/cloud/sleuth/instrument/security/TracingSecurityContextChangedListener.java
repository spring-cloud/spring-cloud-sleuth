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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextChangedEvent;
import org.springframework.security.core.context.SecurityContextChangedListener;

import static java.lang.String.format;
import static org.springframework.cloud.sleuth.instrument.security.SleuthSecuritySpan.SECURITY_CONTEXT_CHANGE;
import static org.springframework.cloud.sleuth.instrument.security.SleuthSecuritySpan.SleuthSecurityEvent;
import static org.springframework.cloud.sleuth.instrument.security.SleuthSecuritySpan.SleuthSecurityEvent.AUTHENTICATION_CLEARED;
import static org.springframework.cloud.sleuth.instrument.security.SleuthSecuritySpan.SleuthSecurityEvent.AUTHENTICATION_REPLACED;
import static org.springframework.cloud.sleuth.instrument.security.SleuthSecuritySpan.SleuthSecurityEvent.AUTHENTICATION_SET;

/**
 * {@link SecurityContextChangedListener} that adds tracing support for Spring Security.
 *
 * @author Jonatan Ivanov
 * @since 3.1.0
 */
public class TracingSecurityContextChangedListener implements SecurityContextChangedListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(TracingSecurityContextChangedListener.class);

	private final Tracer tracer;

	public TracingSecurityContextChangedListener(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public void securityContextChanged(SecurityContextChangedEvent securityContextChangedEvent) {
		SecurityContext previousContext = securityContextChangedEvent.getPreviousContext();
		SecurityContext currentContext = securityContextChangedEvent.getCurrentContext();
		Authentication previousAuthentication = previousContext != null ? previousContext.getAuthentication() : null;
		Authentication currentAuthentication = currentContext != null ? currentContext.getAuthentication() : null;

		if (previousAuthentication != null) {
			if (currentAuthentication != null) {
				attachEvent(AUTHENTICATION_REPLACED, toString(previousAuthentication, currentAuthentication));
			}
			else {
				attachEvent(AUTHENTICATION_CLEARED, toString(previousAuthentication));
			}
		}
		else if (currentAuthentication != null) {
			attachEvent(AUTHENTICATION_SET, toString(currentAuthentication));
		}
		// null-null is not handled since we won't create an event for that case
	}

	private String toString(Authentication previousAuthentication, Authentication currentAuthentication) {
		return toString(previousAuthentication) + " -> " + toString(currentAuthentication);
	}

	private String toString(Authentication authentication) {
		return authentication != null ? authentication.getClass().getSimpleName() + authentication.getAuthorities()
				: "null";
	}

	private void attachEvent(SleuthSecurityEvent sleuthSecurityEvent, String... params) {
		Span span = this.tracer.currentSpan();
		if (span != null) {
			String event = format(sleuthSecurityEvent.getValue(), (Object[]) params);
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(event);
			}
			SECURITY_CONTEXT_CHANGE.wrap(span).event(event);
		}
	}

}
