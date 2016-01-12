/*
 * Copyright 2013-2015 the original author or authors.
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

import java.util.HashMap;
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
 * The {@link ExecutorChannelInterceptor} implementation responsible for the {@link Span}
 * propagation from one message flow's thread to another through the
 * {@link MessageChannel}s involved in the flow.
 * <p>
 * In addition this interceptor cleans up (restores) the {@link Span} in the containers
 * Threads for channels like
 * {@link org.springframework.integration.channel.ExecutorChannel} and
 * {@link org.springframework.integration.channel.QueueChannel}.
 * @author Spencer Gibb
 * @since 1.0
 */
public class TraceContextPropagationChannelInterceptor extends ChannelInterceptorAdapter
		implements ExecutorChannelInterceptor {

	private final TraceManager traceManager;

	private final static ThreadLocal<Trace> ORIGINAL_CONTEXT = new ThreadLocal<>();

	public TraceContextPropagationChannelInterceptor(TraceManager traceManager) {
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
		}
		else {
			return message;
		}
	}

	@Override
	public final Message<?> postReceive(Message<?> message, MessageChannel channel) {
		if (message instanceof MessageWithSpan) {
			MessageWithSpan messageWithSpan = (MessageWithSpan) message;
			Message<?> messageToHandle = messageWithSpan.message;
			populatePropagatedContext(messageWithSpan.span, messageToHandle, channel);
			return message;
		}
		return message;
	}

	@Override
	public void afterMessageHandled(Message<?> message, MessageChannel channel,
			MessageHandler handler, Exception ex) {
		resetPropagatedContext();
	}

	@Override
	public final Message<?> beforeHandle(Message<?> message, MessageChannel channel,
			MessageHandler handler) {
		return postReceive(message, channel);
	}

	private String getParentId(Span span) {
		return span.getParents() != null && !span.getParents().isEmpty()
				? span.getParents().get(0) : null;
	}

	protected void populatePropagatedContext(Span span, Message<?> message,
			MessageChannel channel) {
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

		private final MessageHeaders messageHeaders;

		public MessageWithSpan(Message<?> message, Span span) {
			Assert.notNull(message, "message can not be null");
			Assert.notNull(span, "span can not be null");
			this.message = message;
			this.span = span;

			Map<String, Object> headers = new HashMap<>();
			headers.putAll(message.getHeaders());

			setHeader(headers, Trace.SPAN_ID_NAME, this.span.getSpanId());
			setHeader(headers, Trace.TRACE_ID_NAME, this.span.getTraceId());
			setHeader(headers, Trace.SPAN_NAME_NAME, this.span.getName());
			String parentId = getParentId(span);
			if (parentId != null) {
				setHeader(headers, Trace.PARENT_ID_NAME, parentId);
			}
			String processId = this.span.getProcessId();
			if (processId != null) {
				setHeader(headers, Trace.PROCESS_ID_NAME, processId);
			}
			this.messageHeaders = new MessageHeaders(headers);
		}

		public void setHeader(Map<String, Object> headers, String name, String value) {
			if (!headers.containsKey(name)) {
				headers.put(name, value);
			}
		}

		@Override
		public Object getPayload() {
			return this.message.getPayload();
		}

		@Override
		public MessageHeaders getHeaders() {
			return this.messageHeaders;
		}

		@Override
		public String toString() {
			return "MessageWithSpan{" + "message=" + this.message + ", span=" + this.span
					+ ", messageHeaders=" + this.messageHeaders + '}';
		}

	}
}