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

package org.springframework.cloud.sleuth.api;

public interface ScopedSpan {

	/**
	 * When true, no recording will take place, so no data is reported on finish. However,
	 * the trace context is in scope until {@link #finish()} is called.
	 *
	 * @since 4.19
	 */
	boolean isNoop();

	/**
	 * Returns the trace context associated with this span
	 *
	 * @since 4.19
	 */
	// This api is exposed as there's always a context in scope by definition, and the
	// context is
	// needed for methods like BaggageField.updateValue
	TraceContext context();

	/**
	 * {@inheritDoc}
	 *
	 * @since 5.11
	 */
	ScopedSpan name(String name);

	/**
	 * {@inheritDoc}
	 *
	 * @since 4.19
	 */
	ScopedSpan tag(String key, String value);

	/**
	 * {@inheritDoc}
	 *
	 * @since 4.19
	 */
	ScopedSpan annotate(String value);

	/**
	 * Records an error that impacted this operation.
	 *
	 * <p>
	 * <em>Note:</em> Calling this does not {@linkplain #finish() finish} the span.
	 *
	 * @since 4.19
	 */
	ScopedSpan error(Throwable throwable);

	/**
	 * Closes the scope associated with this span, then reports the span complete,
	 * assigning the most precise duration possible.
	 *
	 * @since 4.19
	 */
	void finish();

}
