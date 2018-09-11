package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.Map;
import java.util.Random;

import org.springframework.cloud.sleuth.B3Utils;
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
		boolean spanIdMissing = !hasSpanId(carrier);
		boolean traceIdMissing = !hasTraceId(carrier);
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

	private boolean hasTraceId(Map<String, String> carrier) {
		return hasHeader(carrier, TraceMessageHeaders.B3_NAME) ||
				hasHeader(carrier, TraceMessageHeaders.TRACE_ID_NAME);
	}

	private boolean hasSpanId(Map<String, String> carrier) {
		return hasHeader(carrier, TraceMessageHeaders.B3_NAME) ||
				hasHeader(carrier, TraceMessageHeaders.SPAN_ID_NAME);
	}

	private String generateTraceIdIfMissing(Map<String, String> carrier,
			boolean traceIdMissing) {
		if (traceIdMissing) {
			long id = this.random.nextLong();
			carrier.put(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(id));
		}
		return traceId(carrier);
	}

	private Span extractSpanFromHeaders(Map<String, String> carrier,
			Span.SpanBuilder spanBuilder, boolean idMissing) {
		String traceId = traceId(carrier);
		spanBuilder = spanBuilder
				.traceIdHigh(traceId.length() == 32 ? Span.hexToId(traceId, 0) : 0)
				.traceId(Span.hexToId(traceId))
				.spanId(Span.hexToId(spanId(carrier)));
		B3Utils.Sampled sampled = sampled(carrier);
		boolean debug = sampled == B3Utils.Sampled.DEBUG;
		boolean spanSampled = sampled == B3Utils.Sampled.SAMPLED;
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

	private String traceId(Map<String, String> carrier) {
		return B3Utils.traceId(TraceMessageHeaders.B3_NAME,
				TraceMessageHeaders.TRACE_ID_NAME, carrier);
	}

	private String spanId(Map<String, String> carrier) {
		return B3Utils.spanId(TraceMessageHeaders.B3_NAME,
				TraceMessageHeaders.SPAN_ID_NAME, carrier);
	}

	private B3Utils.Sampled sampled(Map<String, String> carrier) {
		return B3Utils.sampled(TraceMessageHeaders.B3_NAME,
				TraceMessageHeaders.SAMPLED_NAME,
				TraceMessageHeaders.SPAN_FLAGS_NAME, carrier);
	}

	boolean hasHeader(Map<String, String> message, String name) {
		return message.containsKey(name);
	}

	private void setParentIdIfApplicable(Map<String, String> carrier, Span.SpanBuilder spanBuilder,
			String spanParentIdHeader) {
		String parentId = B3Utils.parentSpanId(TraceMessageHeaders.B3_NAME, spanParentIdHeader, carrier);
		if (parentId != null) {
			spanBuilder.parent(Span.hexToId(parentId));
		}
	}

	private String unprefixedKey(String key) {
		return key.substring(key.indexOf(TraceMessageHeaders.HEADER_DELIMITER) + 1).toLowerCase();
	}

}
