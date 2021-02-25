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

package org.springframework.cloud.sleuth.http;

import org.springframework.lang.Nullable;

/**
 * This API is taken from OpenZipkin Brave.
 *
 * Abstract response type used for parsing and sampling. Represents an HTTP request.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface HttpRequest extends Request {

	/**
	 * @return HTTP method.
	 */
	String method();

	/**
	 * @return HTTP path or {@code null} if not set.
	 */
	@Nullable
	String path();

	/**
	 * Returns an expression such as "/items/:itemId" representing an application
	 * endpoint, conventionally associated with the tag key "http.route". If no route
	 * matched, "" (empty string) is returned. {@code null} indicates this instrumentation
	 * doesn't understand http routes.
	 * @return HTTP route or {@code null} if not set.
	 */
	@Nullable
	default String route() {
		return null;
	}

	/**
	 * @return HTTP URL or {@code null} if not set.
	 */
	@Nullable
	String url();

	/**
	 * @param name header name
	 * @return HTTP header or {@code null} if not set.
	 */
	@Nullable
	String header(String name);

	/**
	 * @return remote IP for the given connection.
	 */
	default String remoteIp() {
		return null;
	}

	/**
	 * @return remote port for the given connection.
	 */
	default int remotePort() {
		return 0;
	}

}
