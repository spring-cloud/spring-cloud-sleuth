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

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.EventValue;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextChangedEvent;

/**
 * {@link DocumentedSpan} for Spring Security Instrumentation.
 *
 * @author Jonatan Ivanov
 * @since 3.1.0
 */
enum SleuthSecuritySpan implements DocumentedSpan {

	/**
	 * Indicates that a {@link SecurityContextChangedEvent} happened during the current
	 * span.
	 */
	SECURITY_CONTEXT_CHANGE;

	@Override
	public String getName() {
		return "security context change";
	}

	@Override
	public EventValue[] getEvents() {
		return SleuthSecurityEvent.values();
	}

	enum SleuthSecurityEvent implements EventValue {

		/**
		 * Event created when an {@link Authentication} object is added to the
		 * {@link SecurityContext}.
		 */
		AUTHENTICATION_SET {
			@Override
			public String getValue() {
				return "Authentication set %s";
			}
		},

		/**
		 * Event created when an {@link Authentication} object is replaced with a new one
		 * in the {@link SecurityContext}.
		 */
		AUTHENTICATION_REPLACED {
			@Override
			public String getValue() {
				return "Authentication replaced %s";
			}
		},

		/**
		 * Event created when an {@link Authentication} object is removed from the
		 * {@link SecurityContext}.
		 */
		AUTHENTICATION_CLEARED {
			@Override
			public String getValue() {
				return "Authentication cleared %s";
			}
		}

	}

}
