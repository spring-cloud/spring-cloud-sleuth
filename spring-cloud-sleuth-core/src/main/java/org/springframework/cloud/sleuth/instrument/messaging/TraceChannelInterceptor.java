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

package org.springframework.cloud.sleuth.instrument.messaging;

import org.springframework.cloud.sleuth.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

/**
 * A channel interceptor that automatically starts / continues / closes and detaches
 * spans.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class TraceChannelInterceptor extends AbstractTraceChannelInterceptor {

	public TraceChannelInterceptor(Tracer tracer, TraceKeys traceKeys,
			SpanExtractor<Message<?>> spanExtractor,
			SpanInjector<MessageBuilder<?>> spanInjector) {
		super(tracer, traceKeys, spanExtractor, spanInjector);
	}

	@Override
	public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
		Span spanFromHeader = getSpanFromHeader(message);
		if (containsServerReceived(spanFromHeader)) {
			spanFromHeader.logEvent(Span.SERVER_SEND);
		} else if (spanFromHeader != null) {
			spanFromHeader.logEvent(Span.CLIENT_RECV);
		}
		getTracer().close(spanFromHeader);
	}

	private boolean containsServerReceived(Span span) {
		if (span == null) {
			return false;
		}
		for (Log log : span.logs()) {
			if (Span.SERVER_RECV.equals(log.getEvent())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		Span parentSpan = getTracer().isTracing() ? getTracer().getCurrentSpan()
				: buildSpan(message);
		String name = getMessageChannelName(channel);
		Span span = startSpan(parentSpan, name, message);
		MessageBuilder<?> messageBuilder = MessageBuilder.fromMessage(message);
		// Backwards compatibility
		if (message.getHeaders().containsKey(TraceMessageHeaders.OLD_MESSAGE_SENT_FROM_CLIENT) ||
				message.getHeaders().containsKey(TraceMessageHeaders.MESSAGE_SENT_FROM_CLIENT)) {
			span.logEvent(Span.SERVER_RECV);
		} else {
			span.logEvent(Span.CLIENT_SEND);
			// Backwards compatibility
			messageBuilder.setHeader(TraceMessageHeaders.OLD_MESSAGE_SENT_FROM_CLIENT, true);
			messageBuilder.setHeader(TraceMessageHeaders.MESSAGE_SENT_FROM_CLIENT, true);
		}
		getSpanInjector().inject(span, messageBuilder);
		return messageBuilder.build();
	}

	private Span startSpan(Span span, String name, Message<?> message) {
		if (span != null) {
			return getTracer().createSpan(name, span);
		}
		// Backwards compatibility
		if (Span.SPAN_NOT_SAMPLED.equals(message.getHeaders().get(Span.SAMPLED_NAME)) ||
				Span.SPAN_NOT_SAMPLED.equals(message.getHeaders().get(TraceMessageHeaders.SAMPLED_NAME))) {
			return getTracer().createSpan(name, NeverSampler.INSTANCE);
		}
		return getTracer().createSpan(name);
	}

	@Override
	public Message<?> beforeHandle(Message<?> message, MessageChannel channel,
			MessageHandler handler) {
		Span spanFromHeader = getSpanFromHeader(message);
		if (spanFromHeader!= null) {
			spanFromHeader.logEvent(Span.SERVER_RECV);
		}
		getTracer().continueSpan(spanFromHeader);
		return message;
	}

	@Override
	public void afterMessageHandled(Message<?> message, MessageChannel channel,
			MessageHandler handler, Exception ex) {
		Span spanFromHeader = getSpanFromHeader(message);
		if (spanFromHeader!= null) {
			spanFromHeader.logEvent(Span.SERVER_SEND);
		}
		getTracer().detach(spanFromHeader);
	}

	private Span getSpanFromHeader(Message<?> message) {
		if (message == null) {
			return null;
		}
		Object object = message.getHeaders().get(TraceMessageHeaders.OLD_SPAN_HEADER);
		if (object instanceof Span) {
			return (Span) object;
		}
		object = message.getHeaders().get(TraceMessageHeaders.SPAN_HEADER);
		if (object instanceof Span) {
			return (Span) object;
		}
		return null;
	}

}
