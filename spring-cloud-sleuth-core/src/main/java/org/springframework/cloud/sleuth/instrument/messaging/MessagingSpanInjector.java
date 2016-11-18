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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.StringUtils;

/**
 * Creates a {@link Span.SpanBuilder} from {@link Message}
 *
 * @author Marcin Grzejszczak
 */
class MessagingSpanInjector implements SpanInjector<MessageBuilder<?>> {

	private final TraceKeys traceKeys;

	public MessagingSpanInjector(TraceKeys traceKeys) {
		this.traceKeys = traceKeys;
	}

	@Override
	public void inject(Span span, MessageBuilder<?> carrier) {
		Message<?> initialMessage = carrier.build();
		MessageHeaderAccessor accessor = MessageHeaderAccessor
				.getMutableAccessor(initialMessage);
		if (span == null) {
			if (!isSampled(initialMessage, Span.SAMPLED_NAME) ||
					!isSampled(initialMessage, TraceMessageHeaders.SAMPLED_NAME)) {
				// Backwards compatibility
				accessor.setHeader(Span.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED);
				accessor.setHeader(TraceMessageHeaders.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED);
				carrier.setHeaders(accessor);
				return;
			}
			return;
		}
		Map<String, String> headers = new HashMap<>();
		addOldHeaders(span, initialMessage, accessor, headers);
		addNewHeaders(span, initialMessage, accessor, headers);
		accessor.copyHeaders(headers);
		if (accessor instanceof NativeMessageHeaderAccessor) {
			NativeMessageHeaderAccessor nativeAccessor = (NativeMessageHeaderAccessor) accessor;
			for (String name : headers.keySet()) {
				nativeAccessor.setNativeHeader(name, headers.get(name));
			}
		}
		carrier.setHeaders(accessor);
	}

	private boolean isSampled(Message<?> initialMessage, String sampledHeaderName) {
		return Span.SPAN_SAMPLED
				.equals(initialMessage.getHeaders().get(sampledHeaderName));
	}

	// Backwards compatibility
	private void addOldHeaders(Span span, Message<?> initialMessage,
			MessageHeaderAccessor accessor, Map<String, String> headers) {
		addHeaders(span, initialMessage, accessor, headers, Span.TRACE_ID_NAME,
				Span.SPAN_ID_NAME, Span.PARENT_ID_NAME, Span.SPAN_NAME_NAME, Span.PROCESS_ID_NAME,
				Span.SAMPLED_NAME, TraceMessageHeaders.OLD_SPAN_HEADER);
	}

	private void addNewHeaders(Span span, Message<?> initialMessage,
			MessageHeaderAccessor accessor, Map<String, String> headers) {
		addHeaders(span, initialMessage, accessor, headers, TraceMessageHeaders.TRACE_ID_NAME,
				TraceMessageHeaders.SPAN_ID_NAME, TraceMessageHeaders.PARENT_ID_NAME, TraceMessageHeaders.SPAN_NAME_NAME,
				TraceMessageHeaders.PROCESS_ID_NAME, TraceMessageHeaders.SAMPLED_NAME, TraceMessageHeaders.SPAN_HEADER);
	}

	private void addHeaders(Span span, Message<?> initialMessage,
			MessageHeaderAccessor accessor, Map<String, String> headers, String traceIdHeader,
			String spanIdHeader, String parentIdHeader, String spanNameHeader, String processIdHeader,
			String spanSampledHeader, String spanHeader) {
		addHeader(headers, traceIdHeader, span.traceIdString());
		addHeader(headers, spanIdHeader, Span.idToHex(span.getSpanId()));
		if (span.isExportable()) {
			addAnnotations(this.traceKeys, initialMessage, span);
			Long parentId = getFirst(span.getParents());
			if (parentId != null) {
				addHeader(headers, parentIdHeader, Span.idToHex(parentId));
			}
			addHeader(headers, spanNameHeader, span.getName());
			addHeader(headers, processIdHeader, span.getProcessId());
			addHeader(headers, spanSampledHeader, Span.SPAN_SAMPLED);
		}
		else {
			addHeader(headers, spanSampledHeader, Span.SPAN_NOT_SAMPLED);
		}
		accessor.setHeader(spanHeader, span);
	}

	private void addAnnotations(TraceKeys traceKeys, Message<?> message, Span span) {
		for (String name : traceKeys.getMessage().getHeaders()) {
			if (message.getHeaders().containsKey(name)) {
				String key = traceKeys.getMessage().getPrefix() + name.toLowerCase();
				Object value = message.getHeaders().get(name);
				if (value == null) {
					value = "null";
				}
				// TODO: better way to serialize?
				tagIfEntryMissing(span, key, value.toString());
			}
		}
		addPayloadAnnotations(traceKeys, message.getPayload(), span);
	}

	private void addPayloadAnnotations(TraceKeys traceKeys, Object payload, Span span) {
		if (payload != null) {
			tagIfEntryMissing(span, traceKeys.getMessage().getPayload().getType(),
					payload.getClass().getCanonicalName());
			if (payload instanceof String) {
				tagIfEntryMissing(span, traceKeys.getMessage().getPayload().getSize(),
						String.valueOf(((String) payload).length()));
			}
			else if (payload instanceof byte[]) {
				tagIfEntryMissing(span, traceKeys.getMessage().getPayload().getSize(),
						String.valueOf(((byte[]) payload).length));
			}
		}
	}

	private void tagIfEntryMissing(Span span, String key, String value) {
		if (!span.tags().containsKey(key)) {
			span.tag(key, value);
		}
	}

	private void addHeader(Map<String, String> headers, String name, String value) {
		if (StringUtils.hasText(value)) {
			headers.put(name, value);
		}
	}

	private Long getFirst(List<Long> parents) {
		return parents.isEmpty() ? null : parents.get(0);
	}
}
