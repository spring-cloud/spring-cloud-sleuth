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

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthWebSpan implements DocumentedSpan {

	/**
	 * Span around a WebFilter. Will continue the current span or create a new one and tag
	 * it
	 */
	WEB_FILTER_SPAN {
		@Override
		public String getName() {
			return "%s";
		}

		@Override
		public TagKey[] getTagKeys() {
			return TagKey.merge(Tags.values(), SecurityTags.values());
		}

	};

	/**
	 * Tags related to web.
	 *
	 * @author Marcin Grzejszczak
	 * @since 3.0.3
	 */
	enum Tags implements TagKey {

		/**
		 * Name of the class that is processing the request.
		 */
		CLASS {
			@Override
			public String getKey() {
				return "mvc.controller.class";
			}
		},

		/**
		 * Name of the method that is processing the request.
		 */
		METHOD {
			@Override
			public String getKey() {
				return "mvc.controller.method";
			}
		},

		/**
		 * Response status code.
		 */
		RESPONSE_STATUS_CODE {
			@Override
			public String getKey() {
				return "http.status_code";
			}
		}

	}

	/**
	 * Tags related to security.
	 *
	 * @author Marcin Grzejszczak
	 * @since 3.1.0
	 */
	enum SecurityTags implements TagKey {

		/**
		 * Authorities assigned to the user.
		 */
		AUTHORITIES {
			@Override
			public String getKey() {
				return "security.authentication.authorities";
			}
		},

		/**
		 * Whether user is authenticated.
		 */
		AUTHENTICATED {
			@Override
			public String getKey() {
				return "security.authentication.authenticated";
			}
		},

		/**
		 * Whether principal is enabled.
		 */
		PRINCIPAL_ENABLED {
			@Override
			public String getKey() {
				return "security.principal.enabled";
			}
		},

		/**
		 * Whether principal's account is non expired.
		 */
		PRINCIPAL_ACCOUNT_NON_EXPIRED {
			@Override
			public String getKey() {
				return "security.principal.account-non-expired";
			}
		},

		/**
		 * Whether principal's credentials are non expired.
		 */
		PRINCIPAL_CREDENTIALS_NON_EXPIRED {
			@Override
			public String getKey() {
				return "security.principal.credentials-non-expired";
			}
		}

	}

}
