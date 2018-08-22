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

package org.springframework.cloud.sleuth.instrument.messaging;

import brave.SpanCustomizer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

/**
 * Allows customization of spans created via {@link TracingChannelInterceptor}
 *
 * @author Marcin Grzejszczak
 * @since 2.1.0
 */
public interface TracingChannelInterceptorCustomizer {

	/**
	 * Customize the created span for the given message and channel
	 *
	 * @param message - message for which a span was created
	 * @param result - the span to customize
	 * @param channel - channel for which a span was created
	 */
	void customize(Message<?> message, SpanCustomizer result, MessageChannel channel);
}
