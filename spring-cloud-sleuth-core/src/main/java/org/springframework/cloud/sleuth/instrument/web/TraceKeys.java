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

import java.util.Collection;
import java.util.LinkedHashSet;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Well-known {@link brave.Span#tag(String, String) span tag} keys.
 * With the deprecation we only left the option to pass a list of
 * HTTP request headers that will be set as tags
 *
 * @since 1.0.0
 *
 * @deprecated the Brave's defaults are suggested to be used
 */
@ConfigurationProperties("spring.sleuth.keys")
@Deprecated
class TraceKeys {

	private Http http = new Http();

	public Http getHttp() {
		return this.http;
	}

	public void setHttp(Http http) {
		this.http = http;
	}

	public static class Http {

		/**
		 * Prefix for header names if they are added as tags.
		 */
		private String prefix = "http.";

		/**
		 * Additional headers that should be added as tags if they exist. If the header
		 * value is multi-valued, the tag value will be a comma-separated, single-quoted
		 * list.
		 */
		private Collection<String> headers = new LinkedHashSet<String>();

		public String getPrefix() {
			return this.prefix;
		}

		public Collection<String> getHeaders() {
			return this.headers;
		}

		public void setPrefix(String prefix) {
			this.prefix = prefix;
		}

		public void setHeaders(Collection<String> headers) {
			this.headers = headers;
		}
	}
}
