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
import static org.springframework.cloud.sleuth.Trace.PROCESS_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.SPAN_NAME_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;
import static org.springframework.util.StringUtils.hasText;

import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceContextHolder;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.cloud.sleuth.MilliSpan.MilliSpanBuilder;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ChannelInterceptorAdapter;

/**
 * Interceptor for Stomp Messages sent over websocket 
 * @author Gaurav Rai Mazra
 * 
 */
public class TraceStompMessageChannelInterceptor extends ChannelInterceptorAdapter implements ChannelInterceptor {
	private ThreadLocal<TraceScope> traceScopeHolder = new ThreadLocal<TraceScope>();
	private ThreadLocal<Span> spanHolder = new ThreadLocal<Span>();

	private final Trace trace;

	public TraceStompMessageChannelInterceptor(Trace trace) {
		this.trace = trace;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		this.spanHolder.set(TraceContextHolder.getCurrentSpan());
		if (TraceContextHolder.isTracing() || message.getHeaders().containsKey(NOT_SAMPLED_NAME)) {
			return StompMessageBuilderHelper.fromMessage(message).setHeadersFromSpan(TraceContextHolder.getCurrentSpan()).build();
		}
		
		String spanId = getHeader(message, SPAN_ID_NAME);
		String traceId = getHeader(message, TRACE_ID_NAME);
		String name = "message/" + getChannelName(channel);
		TraceScope traceScope;
		if (hasText(spanId) && hasText(traceId)) {

			MilliSpanBuilder span = MilliSpan.builder().traceId(traceId).spanId(spanId);
			String parentId = getHeader(message, PARENT_ID_NAME);
			String processId = getHeader(message, PROCESS_ID_NAME);
			String spanName = getHeader(message, SPAN_NAME_NAME);
			if (spanName != null) {
				span.name(spanName);
			}
			if (processId != null) {
				span.processId(processId);
			}
			if (parentId != null) {
				span.parent(parentId);
			}
			span.remote(true);

			// TODO: trace description?
			traceScope = this.trace.startSpan(name, span.build());
		} else {
			traceScope = this.trace.startSpan(name);
		}
		this.traceScopeHolder.set(traceScope);
		return StompMessageBuilderHelper.fromMessage(message).setHeadersFromSpan(traceScope.getSpan()).build();
	}
	
	@Override
	public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
		TraceScope traceScope = this.traceScopeHolder.get();
		if (traceScope != null) {
			traceScope.close();
		}
		
		this.traceScopeHolder.remove();
		// TODO: Maybe the TraceScope could handle this
		TraceContextHolder.setCurrentSpan(this.spanHolder.get());
	}

	private String getChannelName(MessageChannel channel) {
		String name = null;
		if (channel instanceof IntegrationObjectSupport) {
			name = ((IntegrationObjectSupport) channel).getComponentName();
		}
		if (name == null && channel instanceof AbstractMessageChannel) {
			name = ((AbstractMessageChannel) channel).getFullChannelName();
		}
		if (name == null) {
			name = channel.toString();
		}
		return name;
	}

	private String getHeader(Message<?> message, String name) {
		return (String) message.getHeaders().get(name);
	}
}
