/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth;

/**
 * Adds ability to adjust a span before reporting it.
 *
 * IMPORTANT: Your {@link SpanReporter} should inject the collection of {@link SpanAdjuster} and
 * allow {@link Span} manipulation before the actual reporting is done.
 *
 * @author Marcin Grzejszczak
 * @since 1.1.4
 */
public interface SpanAdjuster {
	/**
	 * You can adjust the {@link Span} by creating a new one using the {@link Span.SpanBuilder}
	 * before reporting it.
	 *
	 * In Sleuth we're generating spans with a fixed name. Some users want to modify the name
	 * depending on some values of tags. Implementation of this interface can be used to alter
	 * then name. Example:
	 *
	 * {@code span -> span.toBuilder().name(scrub(span.getName())).build();}
	 */
	Span adjust(Span span);
}
