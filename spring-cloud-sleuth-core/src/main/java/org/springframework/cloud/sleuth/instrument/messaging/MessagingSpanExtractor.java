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
import org.springframework.cloud.sleuth.TraceHeaders;
import org.springframework.messaging.Message;

/**
 * Creates a {@link SpanBuilder} from {@link Message}
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
class MessagingSpanExtractor implements SpanExtractor<Message<?>> {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final Random random;
	private final TraceHeaders traceHeaders;
	private final TraceMessageHeaders traceMessageHeaders;

	public MessagingSpanExtractor(Random random, TraceHeaders traceHeaders,
			TraceMessageHeaders traceMessageHeaders) {
		this.random = random;
		this.traceHeaders = traceHeaders;
		this.traceMessageHeaders = traceMessageHeaders;
	}

	@Override
	public Span joinTrace(Message<?> carrier) {
		if ((!hasHeader(carrier, this.traceHeaders.getTraceId())
				|| !hasHeader(carrier, this.traceHeaders.getSpanId()))
				&& (!hasHeader(carrier, this.traceMessageHeaders.getSpanId())
				|| !hasHeader(carrier, this.traceMessageHeaders.getTraceId()))) {
			return null;
			// TODO: Consider throwing IllegalArgumentException;
		}
		if (hasHeader(carrier, this.traceHeaders.getTraceId())
				|| hasHeader(carrier, this.traceHeaders.getSpanId())) {
			log.warn("Deprecated trace headers detected. Please upgrade Sleuth to 1.1 "
					+ "or start sending headers present in the TraceMessageHeaders class");
			return extractSpanFromOldHeaders(carrier, Span.builder());
		}
		return extractSpanFromNewHeaders(carrier, Span.builder());
	}

	// Backwards compatibility
	private Span extractSpanFromOldHeaders(Message<?> carrier, SpanBuilder spanBuilder) {
		return extractSpanFromHeaders(carrier, spanBuilder, this.traceHeaders.getTraceId(),
				this.traceHeaders.getSpanId(), this.traceHeaders.getSampled(),
				Span.PROCESS_ID_NAME, Span.SPAN_NAME_NAME, this.traceHeaders.getParentId());
	}

	private Span extractSpanFromNewHeaders(Message<?> carrier, SpanBuilder spanBuilder) {
		return extractSpanFromHeaders(carrier, spanBuilder, this.traceMessageHeaders.getTraceId(),
				this.traceMessageHeaders.getSpanId(), this.traceMessageHeaders.getSampled(),
				TraceMessageHeaders.PROCESS_ID_NAME, TraceMessageHeaders.SPAN_NAME_NAME,
				this.traceMessageHeaders.getParentId());
	}

	private Span extractSpanFromHeaders(Message<?> carrier, SpanBuilder spanBuilder,
			String traceIdHeader, String spanIdHeader, String spanSampledHeader,
			String spanProcessIdHeader, String spanNameHeader, String spanParentIdHeader) {
		long traceId = Span
				.hexToId(getHeader(carrier, traceIdHeader));
		long spanId = hasHeader(carrier, spanIdHeader)
				? Span.hexToId(getHeader(carrier, spanIdHeader))
				: this.random.nextLong();
		spanBuilder = spanBuilder.traceId(traceId).spanId(spanId);
		spanBuilder.exportable(
				Span.SPAN_SAMPLED.equals(getHeader(carrier, spanSampledHeader)));
		String processId = getHeader(carrier, spanProcessIdHeader);
		String spanName = getHeader(carrier, spanNameHeader);
		if (spanName != null) {
			spanBuilder.name(spanName);
		}
		if (processId != null) {
			spanBuilder.processId(processId);
		}
		setParentIdIfApplicable(carrier, spanBuilder, spanParentIdHeader);
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

	private void setParentIdIfApplicable(Message<?> carrier, SpanBuilder spanBuilder,
			String spanParentIdHeader) {
		String parentId = getHeader(carrier, spanParentIdHeader);
		if (parentId != null) {
			spanBuilder.parent(Span.hexToId(parentId));
		}
	}
}
