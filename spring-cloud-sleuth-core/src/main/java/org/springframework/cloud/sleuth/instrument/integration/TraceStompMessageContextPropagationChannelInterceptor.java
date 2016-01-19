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

import org.springframework.aop.support.AopUtils;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
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

	private final Tracer tracer;
	private final static ThreadLocal<Span> ORIGINAL_CONTEXT = new ThreadLocal<>();
	private TraceKeys traceKeys;

	public TraceStompMessageContextPropagationChannelInterceptor(Tracer tracer, TraceKeys traceKeys) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
	}

	@Override
	public final Message<?> preSend(Message<?> message, MessageChannel channel) {
		if (DirectChannel.class.isAssignableFrom(AopUtils.getTargetClass(channel))) {
			return message;
		}
		Span span = this.tracer.getCurrentSpan();
		if (span != null) {
			return createMessageWithSpan(message, span);
		} else {
			return message;
		}
	}

	private Message<?> createMessageWithSpan(Message<?> message, Span span) {
		MessageWithSpan output = new MessageWithSpan(message, span);
		addAnnotationsToSpanFromMessage(output, span);
		return output;
	}

	private void addAnnotationsToSpanFromMessage(Message<?> message, Span span) {
		SpanMessageHeaders.addAnnotations(this.traceKeys, message, span);
		SpanMessageHeaders.addPayloadAnnotations(this.traceKeys, message.getPayload(), span);
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
			ORIGINAL_CONTEXT.set(this.tracer.continueSpan(span).getSavedSpan());
		}
	}

	protected void resetPropagatedContext() {
		Span originalContext = ORIGINAL_CONTEXT.get();
		this.tracer.detach(originalContext);
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
