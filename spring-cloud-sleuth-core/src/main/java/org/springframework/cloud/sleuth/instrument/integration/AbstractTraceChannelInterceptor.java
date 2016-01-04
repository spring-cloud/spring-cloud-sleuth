package org.springframework.cloud.sleuth.instrument.integration;

import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.util.StringUtils;

/**
 * Abstraction over classes related to channel intercepting
 *
 * @author Marcin Grzejszczak
 */
abstract class AbstractTraceChannelInterceptor extends ChannelInterceptorAdapter {

	protected final TraceManager traceManager;

	protected AbstractTraceChannelInterceptor(TraceManager traceManager) {
		this.traceManager = traceManager;
	}

	/**
	 * Returns a span given the message and a channel. Returns null when there was no
	 * trace id passed initially.
	 */
	Span buildSpan(Message<?> message) {
		String spanId = getHeader(message, Trace.SPAN_ID_NAME);
		String traceId = getHeader(message, Trace.TRACE_ID_NAME);
		if (StringUtils.hasText(traceId)) {
			MilliSpan.MilliSpanBuilder span = MilliSpan.builder().traceId(traceId).spanId(spanId);
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
			return span.build();
		}
		return null;
	}

	String getHeader(Message<?> message, String name) {
		return (String) message.getHeaders().get(name);
	}

	String getChannelName(MessageChannel channel) {
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

	String getMessageChannelName(MessageChannel channel) {
		return "message/" + getChannelName(channel);
	}

}
