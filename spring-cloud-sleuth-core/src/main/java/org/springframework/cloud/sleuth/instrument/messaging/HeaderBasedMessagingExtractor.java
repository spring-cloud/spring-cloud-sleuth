package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.cloud.sleuth.util.TextMapUtil;

/**
 * Default implementation for messaging
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public class HeaderBasedMessagingExtractor implements MessagingSpanTextMapExtractor {

	@Override
	public Span joinTrace(SpanTextMap textMap) {
		Map<String, String> carrier = TextMapUtil.asMap(textMap);
		if (!hasHeader(carrier, TraceMessageHeaders.SPAN_ID_NAME)
				|| !hasHeader(carrier, TraceMessageHeaders.TRACE_ID_NAME)) {
			return null;
			// TODO: Consider throwing IllegalArgumentException;
		}
		return extractSpanFromHeaders(carrier, Span.builder());
	}

	private Span extractSpanFromHeaders(Map<String, String> carrier, Span.SpanBuilder builder) {
		Span.SpanBuilder spanBuilder = builder;
		long traceId = Span
				.hexToId(carrier.get(TraceMessageHeaders.TRACE_ID_NAME));
		long spanId = Span.hexToId(carrier.get(TraceMessageHeaders.SPAN_ID_NAME));
		spanBuilder = spanBuilder.traceId(traceId).spanId(spanId);
		spanBuilder.exportable(
				Span.SPAN_SAMPLED.equals(carrier.get(TraceMessageHeaders.SAMPLED_NAME)));
		String processId = carrier.get(TraceMessageHeaders.PROCESS_ID_NAME);
		String spanName = carrier.get(TraceMessageHeaders.SPAN_NAME_NAME);
		if (spanName != null) {
			spanBuilder.name(spanName);
		}
		if (processId != null) {
			spanBuilder.processId(processId);
		}
		setParentIdIfApplicable(carrier, spanBuilder, TraceMessageHeaders.PARENT_ID_NAME);
		spanBuilder.remote(true);
		return spanBuilder.build();
	}

	boolean hasHeader(Map<String, String> message, String name) {
		return message.containsKey(name);
	}

	private void setParentIdIfApplicable(Map<String, String> carrier, Span.SpanBuilder spanBuilder,
			String spanParentIdHeader) {
		String parentId = carrier.get(spanParentIdHeader);
		if (parentId != null) {
			spanBuilder.parent(Span.hexToId(parentId));
		}
	}

}
