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

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
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

	public TraceChannelInterceptor(BeanFactory beanFactory) {
		super(beanFactory);
	}

	@Override
	public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
		Span currentSpan = getTracer().getCurrentSpan();
		if (containsServerReceived(currentSpan)) {
			currentSpan.logEvent(Span.SERVER_SEND);
		} else if (currentSpan != null) {
			currentSpan.logEvent(Span.CLIENT_RECV);
		}
		addErrorTag(ex);
		getTracer().close(currentSpan);
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
		Message<?> retrievedMessage = getMessage(message);
		MessageBuilder<?> messageBuilder = MessageBuilder.fromMessage(retrievedMessage);
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
		return new GenericMessage<>(retrievedMessage.getPayload(), headers.getMessageHeaders());
	}

	private Message getMessage(Message<?> message) {
		Object payload = message.getPayload();
		if (payload instanceof MessageDeliveryException) {
			MessageDeliveryException e = (MessageDeliveryException) payload;
			return e.getFailedMessage();
		}
		return message;
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
		Span spanFromHeader = getTracer().getCurrentSpan();
		if (spanFromHeader!= null) {
			spanFromHeader.logEvent(Span.SERVER_RECV);
		}
		getTracer().continueSpan(spanFromHeader);
		return message;
	}

	@Override
	public void afterMessageHandled(Message<?> message, MessageChannel channel,
			MessageHandler handler, Exception ex) {
		Span spanFromHeader = getTracer().getCurrentSpan();
		if (spanFromHeader!= null) {
			spanFromHeader.logEvent(Span.SERVER_SEND);
			addErrorTag(ex);
		}
		// related to #447
		if (getTracer().isTracing()) {
			getTracer().detach(spanFromHeader);
		}
	}

	private void addErrorTag(Exception ex) {
		if (ex != null) {
			getErrorParser().parseErrorTags(getTracer().getCurrentSpan(), ex);
		}
	}

}
