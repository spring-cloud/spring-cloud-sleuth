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

package org.springframework.cloud.sleuth;

import zipkin2.Span;

/**
 * Adds ability to adjust a span before reporting it.
 *
 * <b>IMPORTANT</b> - if you override the default {@link brave.Tracing} implementation,
 * remember to ensure that you pass to it an adjusted version of the {@link zipkin2.reporter.Reporter<zipkin2.Span>}
 * bean. In other words you must reuse the list of available {@link SpanAdjuster}s and
 * wrap the provided {@link zipkin2.reporter.Reporter} interface with it.
 *
 * @author Marcin Grzejszczak
 * @since 1.1.4
 */
public interface SpanAdjuster {
	/**
	 * You can adjust the {@link zipkin2.Span} by creating a new one using the {@link Span#toBuilder()}
	 * before reporting it.
	 *
	 * With the legacy Sleuth approach we're generating spans with a fixed name. Some users want to modify the name
	 * depending on some values of tags. Implementation of this interface can be used to alter
	 * then name. Example:
	 *
	 * {@code span -> span.toBuilder().name(scrub(span.getName())).build();}
	 */
	Span adjust(Span span);
}