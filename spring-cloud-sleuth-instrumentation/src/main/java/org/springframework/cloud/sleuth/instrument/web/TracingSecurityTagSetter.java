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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.cloud.sleuth.instrument.web.SleuthWebSpan.SecurityTags;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.util.StringUtils;

final class TracingSecurityTagSetter {

	private static final Log log = LogFactory.getLog(TracingSecurityTagSetter.class);

	private TracingSecurityTagSetter() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	static void setSecurityTags(Span span, Authentication authentication) {
		if (log.isDebugEnabled()) {
			log.debug("Will set security tags on span [" + span + "]");
		}
		AssertingSpan assertingSpan = AssertingSpan.of(SleuthWebSpan.WEB_FILTER_SPAN, span);
		assertingSpan.tag(SecurityTags.AUTHORITIES,
				StringUtils.collectionToCommaDelimitedString(authentication.getAuthorities()));
		assertingSpan.tag(SecurityTags.AUTHENTICATED, String.valueOf(authentication.isAuthenticated()));
		Object principal = authentication.getPrincipal();
		if (principal instanceof User) {
			User user = (User) principal;
			assertingSpan.tag(SecurityTags.PRINCIPAL_ENABLED, String.valueOf(user.isEnabled()));
			assertingSpan.tag(SecurityTags.PRINCIPAL_AUTHORITIES,
					StringUtils.collectionToCommaDelimitedString(user.getAuthorities()));
			assertingSpan.tag(SecurityTags.PRINCIPAL_ACCOUNT_NON_EXPIRED, String.valueOf(user.isAccountNonExpired()));
			assertingSpan.tag(SecurityTags.PRINCIPAL_CREDENTIALS_NON_EXPIRED,
					String.valueOf(user.isCredentialsNonExpired()));
		}
	}

}
