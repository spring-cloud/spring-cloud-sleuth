package org.springframework.cloud.sleuth.instrument.integration;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptorAdapter;

import java.util.Random;

/**
 * Abstraction over classes related to channel intercepting
 *
 * @author Marcin Grzejszczak
 */
abstract class AbstractTraceChannelInterceptor extends ChannelInterceptorAdapter {

	private final Tracer tracer;

	private final Random random;

	private final TraceKeys traceKeys;

	protected AbstractTraceChannelInterceptor(Tracer tracer, TraceKeys traceKeys, Random random) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.random = random;
	}

	protected Tracer getTracer() {
		return this.tracer;
	}

	protected TraceKeys getTraceKeys() {
		return this.traceKeys;
	}

	/**
	 * Returns a span given the message and a channel. Returns null when there was no
	 * trace id passed initially.
	 */
	Span buildSpan(Message<?> message) {
		if (!hasHeader(message, Span.TRACE_ID_NAME) || !hasHeader(message, Span.SPAN_ID_NAME)) {
			return null; // cannot build a span without ids
		}
		long spanId = hasHeader(message, Span.SPAN_ID_NAME) ?
				getHeader(message, Span.SPAN_ID_NAME, Long.class) : this.random.nextLong();
		long traceId = getHeader(message, Span.TRACE_ID_NAME, Long.class);
		Span.SpanBuilder span = Span.builder().traceId(traceId).spanId(spanId);
		Long parentId = getHeader(message, Span.PARENT_ID_NAME, Long.class);
		if (message.getHeaders().containsKey(Span.NOT_SAMPLED_NAME)) {
			span.exportable(false);
		}
		String processId = getHeader(message, Span.PROCESS_ID_NAME);
		String spanName = getHeader(message, Span.SPAN_NAME_NAME);
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

	String getHeader(Message<?> message, String name) {
		return getHeader(message, name, String.class);
	}

	<T> T getHeader(Message<?> message, String name, Class<T> type) {
		return message.getHeaders().get(name, type);
	}

	boolean hasHeader(Message<?> message, String name) {
		return message.getHeaders().containsKey(name);
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
