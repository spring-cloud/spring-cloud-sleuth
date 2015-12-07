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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;

/**
 * Helper class to create STOMP message
 * @author Gaurav Rai Mazra
 *
 */
public class StompMessageBuilderHelper {
	public static StompMessageBuilder fromMessage(Message<?> message) {
		return new StompMessageBuilder(message);
	}
	
	public static void addAnnotationsToSpanFromMessage(Message<?> message, Span span) {
		for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
			if (!Trace.HEADERS.contains(entry.getKey())) {
				String key = "/messaging/headers/" + entry.getKey().toLowerCase();
				String value = entry.getValue() == null ? null : entry.getValue().toString();
				span.addAnnotation(key, value);
			}
		}
		
		Object payload = message.getPayload();
		if (payload != null) {
			span.addAnnotation("/messaging/payload/type", payload.getClass().getCanonicalName());
			
			if (payload instanceof String) {
				span.addAnnotation("/messaging/payload/size", String.valueOf(((String)payload).length()));
			} else if (payload instanceof byte[]) {
				span.addAnnotation("/messaging/payload/size", String.valueOf(((byte[])payload).length));
			}
		}
	}
	
	static class StompMessageBuilder {
		private Map<String, Object> headers = new TreeMap<String, Object>();
		private Message<?> message;
		
		public StompMessageBuilder(final Message<?> message) {
			this.message = message;
		}
		
		public StompMessageBuilder setHeader(String key, Object value) {
			this.headers.put(key, value);
			return this;
		}

		public StompMessageBuilder setHeaderIfAbsent(String key, Object value) {
			this.headers.putIfAbsent(key, value);
			return this;
		}

		public StompMessageBuilder setHeadersFromSpan(final Span span) {
			if (span != null) {
				setHeaderIfAbsent(Trace.SPAN_ID_NAME, span.getSpanId());
				setHeaderIfAbsent(Trace.TRACE_ID_NAME, span.getTraceId());
				setHeaderIfAbsent(Trace.SPAN_NAME_NAME, span.getName());
				String parentId = getParentId(TraceContextHolder.getCurrentSpan());
				if (parentId != null)
					setHeaderIfAbsent(Trace.PARENT_ID_NAME, parentId);

				String processId = span.getProcessId();
				if (processId != null)
					setHeaderIfAbsent(Trace.PROCESS_ID_NAME, processId);
			}
			return this;
		}

		public Message<?> build() {
			SimpMessageHeaderAccessor messageHeaderAssessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
			String key;
			Object value;
			for (Map.Entry<String, Object> entry : this.headers.entrySet()) {
				key = entry.getKey();
				if (key != null) {
					value = entry.getValue();
					pushHeaders(messageHeaderAssessor, key, value);
				}
			}

			return org.springframework.messaging.support.MessageBuilder.createMessage(this.message.getPayload(),
					messageHeaderAssessor.getMessageHeaders());
		}

		private void pushHeaders(final SimpMessageHeaderAccessor assessor, final String key, final Object value) {
			switch (key) {
			case SimpMessageHeaderAccessor.DESTINATION_HEADER:
			case SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER:
			case SimpMessageHeaderAccessor.SESSION_ID_HEADER:
			case SimpMessageHeaderAccessor.SESSION_ATTRIBUTES:
			case SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER:
			case SimpMessageHeaderAccessor.USER_HEADER:
			case SimpMessageHeaderAccessor.CONNECT_MESSAGE_HEADER:
			case SimpMessageHeaderAccessor.HEART_BEAT_HEADER:
			case SimpMessageHeaderAccessor.ORIGINAL_DESTINATION:
			case SimpMessageHeaderAccessor.IGNORE_ERROR:
				assessor.setHeader(key, value);
				break;
			default:
				assessor.setNativeHeader(key, value == null ? null : value.toString());
			}
		}

		private String getParentId(final Span currentSpan) {
			List<String> parents = currentSpan.getParents();
			return parents == null || parents.isEmpty() ? null : parents.get(0);
		}
	}
}
