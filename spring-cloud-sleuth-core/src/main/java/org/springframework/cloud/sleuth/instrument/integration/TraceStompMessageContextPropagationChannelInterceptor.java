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

import java.util.Map;

import org.springframework.aop.support.AopUtils;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.util.Assert;

/**
 *
 * @author Gaurav Rai Mazra
 * @author Marcin Grzejszczak
 *
 */
public class TraceStompMessageContextPropagationChannelInterceptor extends ChannelInterceptorAdapter
		implements ExecutorChannelInterceptor {

	private final TraceManager traceManager;
	private final static ThreadLocal<Trace> ORIGINAL_CONTEXT = new ThreadLocal<>();

	public TraceStompMessageContextPropagationChannelInterceptor(TraceManager traceManager) {
		this.traceManager = traceManager;
	}

	@Override
	public final Message<?> preSend(Message<?> message, MessageChannel channel) {
		if (DirectChannel.class.isAssignableFrom(AopUtils.getTargetClass(channel))) {
			return message;
		}
		Span span = this.traceManager.getCurrentSpan();
		if (span != null) {
			return new MessageWithSpan(message, span);
		} else {
			return message;
		}
	}

	@Override
	public final Message<?> postReceive(Message<?> message, MessageChannel channel) {
		if (message instanceof MessageWithSpan) {
			MessageWithSpan messageWithSpan = (MessageWithSpan) message;
			populatePropagatedContext(messageWithSpan.span);
			return message;
		}
		return message;
	}

	@Override
	public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
		resetPropagatedContext();
	}

	@Override
	public final Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
		return postReceive(message, channel);
	}

	protected void populatePropagatedContext(Span span) {
		if (span != null) {
			ORIGINAL_CONTEXT.set(this.traceManager.continueSpan(span).getSaved());
		}
	}

	protected void resetPropagatedContext() {
		Trace originalContext = ORIGINAL_CONTEXT.get();
		this.traceManager.detach(originalContext);
		ORIGINAL_CONTEXT.remove();
	}

	private class MessageWithSpan implements Message<Object> {

		private final Message<?> message;
		private final Span span;

		public MessageWithSpan(Message<?> message, Span span) {
			Assert.notNull(message, "message can not be null");
			Assert.notNull(span, "span can not be null");
			this.span = span;
			this.message = StompMessageBuilder.fromMessage(message).setHeadersFromSpan(this.span).build();
			addAnnotationsToSpanFromMessage(this.message, this.span);
		}

		private void addAnnotationsToSpanFromMessage(Message<?> message, Span span) {
			for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
				if (!Trace.HEADERS.contains(entry.getKey())) {
					String key = "/messaging/headers/" + entry.getKey().toLowerCase();
					String value = entry.getValue() == null ? null : entry.getValue().toString();
					span.tag(key, value);
				}
			}
			SpanMessageHeaders.addPayloadAnnotations(message.getPayload(), span);
		}

		@Override
		public Object getPayload() {
			return this.message.getPayload();
		}

		@Override
		public MessageHeaders getHeaders() {
			return this.message.getHeaders();
		}

		@Override
		public String toString() {
			return "MessageWithSpan{" + "message=" + this.message + ", span=" + this.span + "}";
		}

	}

}
