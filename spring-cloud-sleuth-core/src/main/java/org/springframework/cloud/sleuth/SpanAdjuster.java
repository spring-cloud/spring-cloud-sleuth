/*
 * Copyright 2013-2019 the original author or authors.
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
 * Deprecated Span Adjuster.
 *
 * @deprecated use {@link brave.handler.FinishedSpanHandler}
 * @author Marcin Grzejszczak
 */
@Deprecated
public interface SpanAdjuster {

	/**
	 * You can adjust the {@link zipkin2.Span} by creating a new one using the
	 * {@link Span#toBuilder()} before reporting it.
	 *
	 * With the legacy Sleuth approach we're generating spans with a fixed name. Some
	 * users want to modify the name depending on some values of tags. Implementation of
	 * this interface can be used to alter then name. Example:
	 *
	 * {@code span -> span.toBuilder().name(scrub(span.getName())).build();}
	 * @param span to adjust
	 * @return - adjusted span
	 */
	Span adjust(Span span);

}
