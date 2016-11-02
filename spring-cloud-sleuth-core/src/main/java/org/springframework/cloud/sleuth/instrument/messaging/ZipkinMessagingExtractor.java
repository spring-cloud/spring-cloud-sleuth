package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;

/**
 * Default implementation, compatible with Zipkin propagation.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public class ZipkinMessagingExtractor implements MessagingSpanTextMapExtractor {

	@Override
	public Span joinTrace(SpanTextMap textMap) {
		Map<String, String> carrier = asMap(textMap);
		if (!hasHeader(carrier, TraceMessageHeaders.SPAN_ID_NAME)
				|| !hasHeader(carrier, TraceMessageHeaders.TRACE_ID_NAME)) {
			return null;
			// TODO: Consider throwing IllegalArgumentException;
		}
		return extractSpanFromNewHeaders(carrier, Span.builder());
	}

	private Span extractSpanFromNewHeaders(Map<String, String> carrier, Span.SpanBuilder spanBuilder) {
		return extractSpanFromHeaders(carrier, spanBuilder, TraceMessageHeaders.TRACE_ID_NAME,
				TraceMessageHeaders.SPAN_ID_NAME, TraceMessageHeaders.SAMPLED_NAME,
				TraceMessageHeaders.PROCESS_ID_NAME, TraceMessageHeaders.SPAN_NAME_NAME,
				TraceMessageHeaders.PARENT_ID_NAME);
	}

	private Span extractSpanFromHeaders(Map<String, String> carrier, Span.SpanBuilder spanBuilder,
			String traceIdHeader, String spanIdHeader, String spanSampledHeader,
			String spanProcessIdHeader, String spanNameHeader, String spanParentIdHeader) {
		long traceId = Span
				.hexToId(carrier.get(traceIdHeader));
		long spanId = Span.hexToId(carrier.get(spanIdHeader));
		spanBuilder = spanBuilder.traceId(traceId).spanId(spanId);
		spanBuilder.exportable(
				Span.SPAN_SAMPLED.equals(carrier.get(spanSampledHeader)));
		String processId = carrier.get(spanProcessIdHeader);
		String spanName = carrier.get(spanNameHeader);
		if (spanName != null) {
			spanBuilder.name(spanName);
		}
		if (processId != null) {
			spanBuilder.processId(processId);
		}
		setParentIdIfApplicable(carrier, spanBuilder, spanParentIdHeader);
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

	// TODO: Seems to be faster than iterating with iterator each time
	private Map<String, String> asMap(SpanTextMap carrier) {
		Map<String, String> map = new HashMap<>();
		for (Map.Entry<String, String> entry : carrier) {
			map.put(entry.getKey(), entry.getValue());
		}
		return map;
	}
}
