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

import java.lang.invoke.MethodHandles;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Span.SpanBuilder;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.messaging.Message;

/**
 * Creates a {@link SpanBuilder} from {@link Message}
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class MessagingSpanExtractor implements SpanExtractor<Message<?>> {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final Random random;

	public MessagingSpanExtractor(Random random) {
		this.random = random;
	}

	@Override
	public Span joinTrace(Message<?> carrier) {
		if (!hasHeader(carrier, Span.TRACE_ID_NAME)
				|| !hasHeader(carrier, Span.SPAN_ID_NAME)) {
			return null;
			// TODO: Consider throwing IllegalArgumentException;
		}
		long traceId = getTraceIdOrSetDefault(carrier);
		long spanId = hasHeader(carrier, Span.SPAN_ID_NAME)
				? getSpanIdOrSetDefault(carrier)
				: this.random.nextLong();
		SpanBuilder spanBuilder = Span.builder().traceId(traceId).spanId(spanId);
		spanBuilder.exportable(
				Span.SPAN_SAMPLED.equals(getHeader(carrier, Span.SAMPLED_NAME)));
		String processId = getHeader(carrier, Span.PROCESS_ID_NAME);
		String spanName = getHeader(carrier, Span.SPAN_NAME_NAME);
		if (spanName != null) {
			spanBuilder.name(spanName);
		}
		if (processId != null) {
			spanBuilder.processId(processId);
		}
		setParentIdIfApplicable(carrier, spanBuilder);
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

	private long getTraceIdOrSetDefault(Message<?> carrier) {
		try {
			return Span
					.hexToId(getHeader(carrier, Span.TRACE_ID_NAME));
		} catch (Exception e) {
			long id = this.random.nextLong();
			log.warn("Exception occurred while trying to retrieve the trace "
					+ "id from headers. Will set id to value ["
					+ Span.idToHex(id) + "]", e);
			return id;
		}
	}
	private void setParentIdIfApplicable(Message<?> carrier, SpanBuilder spanBuilder) {
		try {
			String parentId = getHeader(carrier, Span.PARENT_ID_NAME);
			if (parentId != null) {
				spanBuilder.parent(Span.hexToId(parentId));
			}
		} catch (Exception e) {
			log.warn("Exception occurred while trying to set parentId", e);
		}
	}

	private long getSpanIdOrSetDefault(Message<?> carrier) {
		try {
			return Span
					.hexToId(getHeader(carrier, Span.SPAN_ID_NAME));
		} catch (Exception e) {
			long id = this.random.nextLong();
			log.warn("Exception occurred while trying to retrieve the span "
					+ "id from headers. Will set id to value ["
					+ Span.idToHex(id) + "]", e);
			return id;
		}
	}
}
