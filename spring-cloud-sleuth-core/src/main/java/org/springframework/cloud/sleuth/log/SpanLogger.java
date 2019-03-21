/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.log;

import org.springframework.cloud.sleuth.Span;

/**
 * Contract for implementations responsible for logging Spans
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public interface SpanLogger {

	/**
	 * Logic to run when a Span gets started
	 *
	 * @param parent - maybe be nullable
	 * @param span - current span
	 */
	void logStartedSpan(Span parent, Span span);

	/**
	 * Logic to run when a Span gets continued
	 */
	void logContinuedSpan(Span span);

	/**
	 * Logic to run when a Span gets stopped (closed or detached)
	 *
	 * @param parent - maybe be nullable
	 * @param span - current span
	 */
	void logStoppedSpan(Span parent, Span span);
}
