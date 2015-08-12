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

import static org.springframework.cloud.sleuth.Trace.NOT_SAMPLED_NAME;
import static org.springframework.cloud.sleuth.Trace.PARENT_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

/**
 * @author Dave Syer
 *
 */
public class SpanMessageHeaders {

	public static Message<?> addSpanHeaders(Message<?> message, Span span) {
		if (span == null) {
			if (!message.getHeaders().containsKey(NOT_SAMPLED_NAME)) {
				return MessageBuilder.fromMessage(message).setHeader(NOT_SAMPLED_NAME, "")
						.build();
			}
			return message;
		}
		Map<String, String> headers = new HashMap<String, String>();
		addHeader(headers, TRACE_ID_NAME, span.getTraceId());
		addHeader(headers, SPAN_ID_NAME, span.getSpanId());
		addHeader(headers, PARENT_ID_NAME, getFirst(span.getParents()));
		return MessageBuilder.fromMessage(message).copyHeaders(headers).build();
	}

	private static void addHeader(Map<String, String> headers, String name, String value) {
		if (value != null) {
			headers.put(name, value);
		}
	}

	private static String getFirst(List<String> parents) {
		return parents == null || parents.isEmpty() ? null : parents.get(0);
	}

}
