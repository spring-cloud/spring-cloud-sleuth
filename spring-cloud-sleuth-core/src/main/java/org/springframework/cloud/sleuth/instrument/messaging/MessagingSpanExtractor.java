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
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.TraceHeaders;
import org.springframework.messaging.Message;

/**
 * Creates a {@link SpanBuilder} from {@link Message}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
public class MessagingSpanExtractor implements SpanExtractor<Message> {

	private final Random random;
	private final TraceHeaders traceHeaders;

	public MessagingSpanExtractor(Random random, TraceHeaders traceHeaders) {
		this.random = random;
		this.traceHeaders = traceHeaders;
	}

	@Override 
	public Span joinTrace(Message carrier) {
		if (!hasHeader(carrier, this.traceHeaders.getTraceId())
				|| !hasHeader(carrier, this.traceHeaders.getSpanId())) {
			return null;
			//TODO: Consider throwing IllegalArgumentException;
		}
		long spanId = hasHeader(carrier, this.traceHeaders.getSpanId())
				? Span.hexToId(getHeader(carrier, this.traceHeaders.getSpanId()))
				: this.random.nextLong();
		long traceId = Span.hexToId(getHeader(carrier, this.traceHeaders.getTraceId()));
		SpanBuilder spanBuilder = Span.builder().traceId(traceId).spanId(spanId);
		if (TraceHeaders.SPAN_NOT_SAMPLED.equals(
				getHeader(carrier, this.traceHeaders.getSampled()))) {
			spanBuilder.exportable(false);
		}
		String parentId = getHeader(carrier, this.traceHeaders.getParentSpanId());
		String processId = getHeader(carrier, this.traceHeaders.getProcessId());
		String spanName = getHeader(carrier, this.traceHeaders.getSleuth().getSpanName());
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
		return spanBuilder.build();
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
