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

import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.MilliSpan.MilliSpanBuilder;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 *
 */
public class TraceChannelInterceptor extends ChannelInterceptorAdapter {

	private ThreadLocal<Trace> traceHolder = new ThreadLocal<>();

	private final TraceManager traceManager;

	public TraceChannelInterceptor(TraceManager traceManager) {
		this.traceManager = traceManager;
	}

	@Override
	public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
		Trace trace = this.traceHolder.get();
		this.traceManager.close(trace);
		this.traceHolder.remove();
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		if (this.traceManager.isTracing()) {
			return SpanMessageHeaders.addSpanHeaders(message,
					this.traceManager.getCurrentSpan());
		}
		String spanId = getHeader(message, Trace.SPAN_ID_NAME);
		String traceId = getHeader(message, Trace.TRACE_ID_NAME);
		String name = "message/" + getChannelName(channel);
		Trace trace;
		if (StringUtils.hasText(traceId)) {

			MilliSpanBuilder span = MilliSpan.builder().traceId(traceId).spanId(spanId);
			String parentId = getHeader(message, Trace.PARENT_ID_NAME);
			if (message.getHeaders().containsKey(Trace.NOT_SAMPLED_NAME)) {
				span.exportable(false);
			}
			String processId = getHeader(message, Trace.PROCESS_ID_NAME);
			String spanName = getHeader(message, Trace.SPAN_NAME_NAME);
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

			trace = this.traceManager.startSpan(name, span.build());
		}
		else {
			trace = this.traceManager.startSpan(name);
		}
		this.traceHolder.set(trace);
		return SpanMessageHeaders.addSpanHeaders(message, trace.getSpan());
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
