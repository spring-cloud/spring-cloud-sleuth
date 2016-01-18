/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.integration;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for manipulating message headers related to span data.
 *
 * @author Dave Syer
 *
 */
public class SpanMessageHeaders {

	public static Message<?> addSpanHeaders(Message<?> message, Span span) {
		if (span == null) {
			if (!message.getHeaders().containsKey(Trace.NOT_SAMPLED_NAME)) {
				return MessageBuilder.fromMessage(message)
						.setHeader(Trace.NOT_SAMPLED_NAME, "").build();
			}
			return message;
		}

		Map<String, String> headers = new HashMap<>();
		addHeader(headers, Trace.TRACE_ID_NAME, span.getTraceId());
		addHeader(headers, Trace.SPAN_ID_NAME, span.getSpanId());

		if (span.isExportable()) {
			addAnnotations(message, span);
			addHeader(headers, Trace.PARENT_ID_NAME, getFirst(span.getParents()));
			addHeader(headers, Trace.SPAN_NAME_NAME, span.getName());
			addHeader(headers, Trace.PROCESS_ID_NAME, span.getProcessId());
		} else {
			addHeader(headers, Trace.NOT_SAMPLED_NAME, "");
		}
		return MessageBuilder.fromMessage(message).copyHeaders(headers).build();
	}

	public static void addAnnotations(Message<?> message, Span span) {
		for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
			if (!Trace.HEADERS.contains(entry.getKey())) { // filter out trace headers
				String key = "/messaging/headers/" + entry.getKey().toLowerCase();
				String value = null;
				if (entry.getValue() != null) {
					value = entry.getValue().toString(); // TODO: better way to serialize?
				}
				span.tag(key, value);
			}
		}
		addPayloadAnnotations(message.getPayload(), span);
	}

	static void addPayloadAnnotations(Object payload, Span span) {
		if (payload != null) {
			span.tag("/messaging/payload/type",
					payload.getClass().getCanonicalName());
			if (payload instanceof String) {
				span.tag("/messaging/payload/size",
						String.valueOf(((String) payload).length()));
			}
			else if (payload instanceof byte[]) {
				span.tag("/messaging/payload/size",
						String.valueOf(((byte[]) payload).length));
			}
		}
	}

	private static void addHeader(Map<String, String> headers, String name,
			String value) {
		if (StringUtils.hasText(value)) {
			headers.put(name, value);
		}
	}

	private static void addHeader(Map<String, String> headers, String name,
			Long value) {
		if (value != null) {
			addHeader(headers, name, Span.IdConverter.toHex(value));
		}
	}

	private static Long getFirst(List<Long> parents) {
		return parents.isEmpty() ? null : parents.get(0);
	}

}
