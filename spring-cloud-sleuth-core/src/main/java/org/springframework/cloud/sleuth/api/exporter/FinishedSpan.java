/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.api.exporter;

import java.util.Collection;
import java.util.Map;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.lang.Nullable;

/**
 * This API is inspired by OpenZipkin Brave (from {code MutableSpan}).
 *
 * Represents a span that has been finished and is ready to be sent to an external
 * location (e.g. Zipkin).
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface FinishedSpan {

	/**
	 * @return span's name
	 */
	String getName();

	/**
	 * @return span's start timestamp
	 */
	long getStartTimestamp();

	/**
	 * @return span's end timestamp
	 */
	long getEndTimestamp();

	/**
	 * @return span's tags
	 */
	Map<String, String> getTags();

	/**
	 * @return span's events as timestamp to value mapping
	 */
	Collection<Map.Entry<Long, String>> getEvents();

	/**
	 * @return span's span id
	 */
	String getSpanId();

	/**
	 * @return span's parent id or {@code null} if not set
	 */
	@Nullable
	String getParentId();

	/**
	 * @return span's remote ip
	 */
	@Nullable
	String getRemoteIp();

	/**
	 * @return span's remote port
	 */
	int getRemotePort();

	/**
	 * @return span's trace id
	 */
	String getTraceId();

	/**
	 * @return corresponding error or {@code null} if one was not thrown
	 */
	@Nullable
	Throwable getError();

	/**
	 * @return span's kind
	 */
	Span.Kind getKind();

	/**
	 * @return remote service name or {@code null} if not set
	 */
	@Nullable
	String getRemoteServiceName();

}
