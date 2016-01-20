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
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.Random;

/**
 * Interceptor for Stomp Messages sent over websocket
 *
 * @author Gaurav Rai Mazra
 * @author Marcin Grzejszczak
 *
 */
public class TraceStompMessageChannelInterceptor extends AbstractTraceChannelInterceptor implements ChannelInterceptor {
	private ThreadLocal<Span> traceScopeHolder = new ThreadLocal<>();

	public TraceStompMessageChannelInterceptor(Tracer tracer, TraceKeys traceKeys, Random random) {
		super(tracer, traceKeys, random);
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		if (getTracer().isTracing() || message.getHeaders().containsKey(Span.NOT_SAMPLED_NAME)) {
			return StompMessageBuilder.fromMessage(message).setHeadersFromSpan(getTracer().getCurrentSpan()).build();
		}
		String name = getMessageChannelName(channel);
		Span span = startSpan(buildSpan(message), name);
		this.traceScopeHolder.set(span);
		return StompMessageBuilder.fromMessage(message).setHeadersFromSpan(span).build();
	}

	private Span startSpan(Span span, String name) {
		if (span != null) {
			return getTracer().joinTrace(name, span);
		}
		return getTracer().startTrace(name);
	}

	@Override
	public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
		final ThreadLocal<Span> traceScopeHolder = this.traceScopeHolder;
		Span traceInScope = traceScopeHolder.get();
		getTracer().close(traceInScope);
		traceScopeHolder.remove();
	}
}
