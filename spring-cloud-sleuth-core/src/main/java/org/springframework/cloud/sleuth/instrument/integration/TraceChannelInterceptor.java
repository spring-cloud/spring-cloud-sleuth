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

import java.util.Random;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.cloud.sleuth.sampler.IsTracingSampler;
import org.springframework.cloud.sleuth.trace.SpanContextHolder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

/**
 * @author Dave Syer
 *
 */
public class TraceChannelInterceptor extends AbstractTraceChannelInterceptor {

	private final ThreadLocal<Span> traceHolder = new ThreadLocal<>();

	public TraceChannelInterceptor(Tracer tracer, TraceKeys traceKeys, Random random) {
		super(tracer, traceKeys, random);
	}

	@Override
	public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
		Span trace = closeAllParentMessageRelatedSpans(this.traceHolder.get());
		this.traceHolder.remove();
		if (trace != null) {
			SpanContextHolder.setCurrentSpan(trace);
		}
	}

	private Span closeAllParentMessageRelatedSpans(Span trace) {
		Span traceToClose = trace;
		while (traceToClose != null && traceToClose.getName() != null &&
				traceToClose.getName().startsWith(MESSAGE_NAME_PREFIX)) {
			traceToClose = getTracer().close(traceToClose);
		}
		return traceToClose;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		Span parentSpan = getTracer().isTracing() ? getTracer().getCurrentSpan() : buildSpan(message);
		String name = getMessageChannelName(channel);
		Span span = startSpan(parentSpan, name, message);
		this.traceHolder.set(span);
		return SpanMessageHeaders.addSpanHeaders(getTraceKeys(), message, span);
	}

	private Span startSpan(Span span, String name, Message<?> message) {
		if (span != null) {
			return getTracer().joinTrace(name, span);
		}
		if (message.getHeaders().containsKey(Span.NOT_SAMPLED_NAME)) {
			return getTracer().startTrace(name, IsTracingSampler.INSTANCE);
		}
		return getTracer().startTrace(name);
	}

}
