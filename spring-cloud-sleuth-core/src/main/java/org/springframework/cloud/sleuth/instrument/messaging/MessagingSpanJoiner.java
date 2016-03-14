/*
 * Copyright 2013-2016 the original author or authors.
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

import java.util.Random;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Span.SpanBuilder;
import org.springframework.cloud.sleuth.SpanJoiner;
import org.springframework.messaging.Message;

/**
 * Creates a {@link SpanBuilder} from {@link Message}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
public class MessagingSpanJoiner implements SpanJoiner {

	private final Random random;

	public MessagingSpanJoiner(Random random) {
		this.random = random;
	}

	/**
	 * Creates a builder from a message.
	 *
	 * @return span builder or null if headers are missing or carrier is not a message
	 */
	@Override 
	public <T> SpanBuilder join(T carrier) {
		if (!(carrier instanceof Message)) {
			return null;
		}
		Message message = (Message) carrier;
		if (!hasHeader(message, Span.TRACE_ID_NAME)
				|| !hasHeader(message, Span.SPAN_ID_NAME)) {
			return null;
			//TODO: Consider throwing IllegalArgumentException;
		}
		long spanId = hasHeader(message, Span.SPAN_ID_NAME)
				? Span.hexToId(getHeader(message, Span.SPAN_ID_NAME))
				: this.random.nextLong();
		long traceId = Span.hexToId(getHeader(message, Span.TRACE_ID_NAME));
		SpanBuilder spanBuilder = Span.builder().traceId(traceId).spanId(spanId);
		if (hasHeader(message, Span.NOT_SAMPLED_NAME)) {
			spanBuilder.exportable(false);
		}
		String parentId = getHeader(message, Span.PARENT_ID_NAME);
		String processId = getHeader(message, Span.PROCESS_ID_NAME);
		String spanName = getHeader(message, Span.SPAN_NAME_NAME);
		if (spanName != null) {
			spanBuilder.name(spanName);
		}
		if (processId != null) {
			spanBuilder.processId(processId);
		}
		if (parentId != null) {
			spanBuilder.parent(Span.hexToId(parentId));
		}
		spanBuilder.remote(true);
		return spanBuilder;
	}

	String getHeader(Message<?> message, String name) {
		return getHeader(message, name, String.class);
	}

	<T> T getHeader(Message<?> message, String name, Class<T> type) {
		return message.getHeaders().get(name, type);
	}

	boolean hasHeader(Message<?> message, String name) {
		return message.getHeaders().containsKey(name);
	}
}
