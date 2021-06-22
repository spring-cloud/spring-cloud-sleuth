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

package org.springframework.cloud.sleuth.instrument.messaging;

import org.springframework.cloud.sleuth.Span;
import org.springframework.messaging.Message;

/**
 * Allows customization of messaging spans for Spring Cloud Function instrumentation.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.4
 */
public interface FunctionMessageSpanCustomizer {

	/**
	 * Customizes the span created after wrapping the input message in a span
	 * representation.
	 * @param span current span to customize
	 * @param message received or sent message
	 */
	default void customizeInputMessageSpan(Span span, Message<?> message) {

	}

	/**
	 * Customizes the span wrapping the function execution.
	 * @param span current span to customize
	 * @param message message to be sent
	 */
	default void customizeFunctionSpan(Span span, Message<?> message) {

	}

	/**
	 * Customizes the span created for the output message
	 * @param span current span to customize
	 * @param message message to be sent
	 */
	default void customizeOutputMessageSpan(Span span, Message<?> message) {

	}

}
