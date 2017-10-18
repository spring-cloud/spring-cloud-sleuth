package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.Map;
import java.util.Random;

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

	private final Random random = new Random();

	@Override
	public Span joinTrace(SpanTextMap textMap) {
		Map<String, String> carrier = TextMapUtil.asMap(textMap);
		boolean spanIdMissing = !hasHeader(carrier, TraceMessageHeaders.SPAN_ID_NAME);
		boolean traceIdMissing = !hasHeader(carrier, TraceMessageHeaders.TRACE_ID_NAME);
		if (Span.SPAN_SAMPLED.equals(carrier.get(TraceMessageHeaders.SPAN_FLAGS_NAME))) {
			String traceId = generateTraceIdIfMissing(carrier, traceIdMissing);
			if (spanIdMissing) {
				carrier.put(TraceMessageHeaders.SPAN_ID_NAME, traceId);
			}
		} else if (spanIdMissing) {
			return null;
			// TODO: Consider throwing IllegalArgumentException;
		}
		boolean idMissing = spanIdMissing || traceIdMissing;
		return extractSpanFromHeaders(carrier, Span.builder(), idMissing);
	}

	private String generateTraceIdIfMissing(Map<String, String> carrier,
			boolean traceIdMissing) {
		if (traceIdMissing) {
			carrier.put(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(this.random.nextLong()));
		}
		return carrier.get(TraceMessageHeaders.TRACE_ID_NAME);
	}

	private Span extractSpanFromHeaders(Map<String, String> carrier,
			Span.SpanBuilder spanBuilder, boolean idMissing) {
		String traceId = carrier.get(TraceMessageHeaders.TRACE_ID_NAME);
		spanBuilder = spanBuilder
				.traceIdHigh(traceId.length() == 32 ? Span.hexToId(traceId, 0) : 0)
				.traceId(Span.hexToId(traceId))
				.spanId(Span.hexToId(carrier.get(TraceMessageHeaders.SPAN_ID_NAME)));
		String flags = carrier.get(TraceMessageHeaders.SPAN_FLAGS_NAME);
		boolean debug = Span.SPAN_SAMPLED.equals(flags);
		boolean spanSampled = Span.SPAN_SAMPLED.equals(carrier.get(TraceMessageHeaders.SAMPLED_NAME));
		if (debug) {
			spanBuilder.exportable(true);
		} else {
			spanBuilder.exportable(spanSampled);
		}
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
		spanBuilder.shared((debug || spanSampled) && !idMissing);
		for (Map.Entry<String, String> entry : carrier.entrySet()) {
			if (entry.getKey().toLowerCase().startsWith(Span.SPAN_BAGGAGE_HEADER_PREFIX + TraceMessageHeaders.HEADER_DELIMITER)) {
				spanBuilder.baggage(unprefixedKey(entry.getKey()), entry.getValue());
			}
		}
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

	private String unprefixedKey(String key) {
		return key.substring(key.indexOf(TraceMessageHeaders.HEADER_DELIMITER) + 1).toLowerCase();
	}

}
