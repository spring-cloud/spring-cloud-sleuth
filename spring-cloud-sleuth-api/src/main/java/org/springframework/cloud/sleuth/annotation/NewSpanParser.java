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

package org.springframework.cloud.sleuth.annotation;

import org.aopalliance.intercept.MethodInvocation;

import org.springframework.cloud.sleuth.Span;

/**
 * Parses data for a span created via a {@link NewSpan} annotation.
 *
 * @author Adrian Cole
 * @since 2.0.0
 */
public interface NewSpanParser {

	/**
	 * Override to control the name and tags on an annotation-based span.
	 * @param methodInvocation method invocation annotated with new span
	 * @param newSpan meta data of the new span
	 * @param span span to customize
	 */
	void parse(MethodInvocation methodInvocation, NewSpan newSpan, Span span);

}
