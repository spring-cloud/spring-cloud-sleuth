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

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.cloud.sleuth.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * A channel interceptor that automatically starts / continues / closes and detaches
 * spans.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class TraceChannelInterceptor extends AbstractTraceChannelInterceptor {

	private final ObjectMapper objectMapper;

	public TraceChannelInterceptor(Tracer tracer, TraceKeys traceKeys,
			MessagingSpanTextMapExtractor spanExtractor,
			MessagingSpanTextMapInjector spanInjector, ObjectMapper objectMapper) {
		super(tracer, traceKeys, spanExtractor, spanInjector);
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
		Span spanFromHeader = getSpanFromHeader(message);
		getTracer().continueSpan(spanFromHeader);
		Span continuedSpan = getTracer().getCurrentSpan();
		if (containsServerReceived(continuedSpan)) {
			continuedSpan.logEvent(Span.SERVER_SEND);
		} else if (spanFromHeader != null) {
			continuedSpan.logEvent(Span.CLIENT_RECV);
		}
		addErrorTag(ex);
		getTracer().close(continuedSpan);
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
		MessageBuilder<?> messageBuilder = MessageBuilder.fromMessage(message);
		Span parentSpan = getTracer().isTracing() ? getTracer().getCurrentSpan()
				: buildSpan(new MessagingTextMap(messageBuilder));
		String name = getMessageChannelName(channel);
		Span span = startSpan(parentSpan, name, message);
		if (message.getHeaders().containsKey(TraceMessageHeaders.MESSAGE_SENT_FROM_CLIENT)) {
			span.logEvent(Span.SERVER_RECV);
		} else {
			span.logEvent(Span.CLIENT_SEND);
			messageBuilder.setHeader(TraceMessageHeaders.MESSAGE_SENT_FROM_CLIENT, true);
		}
		getSpanInjector().inject(span, new MessagingTextMap(messageBuilder));
		MessageHeaderAccessor headers = MessageHeaderAccessor.getMutableAccessor(message);
		headers.copyHeaders(messageBuilder.build().getHeaders());
		return new GenericMessage<Object>(message.getPayload(), headers.getMessageHeaders());
	}

	private Span startSpan(Span span, String name, Message<?> message) {
		if (span != null) {
			return getTracer().createSpan(name, span);
		}
		if (Span.SPAN_NOT_SAMPLED.equals(message.getHeaders().get(TraceMessageHeaders.SAMPLED_NAME))) {
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
			addErrorTag(ex);
		}
		getTracer().detach(spanFromHeader);
	}

	private void addErrorTag(Exception ex) {
		if (ex != null) {
			getTracer().addTag(Span.SPAN_ERROR_TAG_NAME, ExceptionUtils.getExceptionMessage(ex));
		}
	}

	private Span getSpanFromHeader(Message<?> message) {
		if (message == null) {
			return null;
		}
		Object object = message.getHeaders().get(TraceMessageHeaders.SPAN_HEADER);
		if (object instanceof String) {
			return readSpan((String) object);
		}
		return null;
	}

	private Span readSpan(String object) {
		try {
			return this.objectMapper.readValue(object, Span.class);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
