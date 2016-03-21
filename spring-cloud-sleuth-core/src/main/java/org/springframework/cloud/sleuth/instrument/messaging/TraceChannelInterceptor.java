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

	private static final String SPAN_HEADER = "X-Current-Span";

	public TraceChannelInterceptor(Tracer tracer, TraceKeys traceKeys,
			SpanExtractor<Message<?>> spanExtractor,
			SpanInjector<MessageBuilder<?>> spanInjector) {
		super(tracer, traceKeys, spanExtractor, spanInjector);
	}

	@Override
	public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
		getTracer().close(getSpanFromHeader(message));
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		Span parentSpan = getTracer().isTracing() ? getTracer().getCurrentSpan()
				: buildSpan(message);
		String name = getMessageChannelName(channel);
		Span span = startSpan(parentSpan, name, message);
		MessageBuilder<?> messageBuilder = MessageBuilder.fromMessage(message);
		getSpanInjector().inject(span, messageBuilder);
		return messageBuilder.build();
	}

	private Span startSpan(Span span, String name, Message<?> message) {
		if (span != null) {
			return getTracer().createSpan(name, span);
		}
		if (Span.SPAN_NOT_SAMPLED.equals(message.getHeaders().get(Span.SAMPLED_NAME))) {
			return getTracer().createSpan(name, NeverSampler.INSTANCE);
		}
		return getTracer().createSpan(name);
	}

	@Override
	public Message<?> beforeHandle(Message<?> message, MessageChannel channel,
			MessageHandler handler) {
		getTracer().continueSpan(getSpanFromHeader(message));
		return message;
	}

	@Override
	public void afterMessageHandled(Message<?> message, MessageChannel channel,
			MessageHandler handler, Exception ex) {
		getTracer().detach(getSpanFromHeader(message));
	}

	private Span getSpanFromHeader(Message<?> message) {
		if (message == null) {
			return null;
		}
		Object object = message.getHeaders().get(SPAN_HEADER);
		if (object instanceof Span) {
			return (Span) object;
		}
		return null;
	}

}
