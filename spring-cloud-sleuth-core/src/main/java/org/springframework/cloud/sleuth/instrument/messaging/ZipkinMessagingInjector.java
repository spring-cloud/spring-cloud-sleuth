package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.*;
import org.springframework.util.StringUtils;

/**
 * Default implementation, compatible with Zipkin propagation.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public class ZipkinMessagingInjector implements MessagingSpanTextMapInjector {

	private final TraceKeys traceKeys;

	public ZipkinMessagingInjector(TraceKeys traceKeys) {
		this.traceKeys = traceKeys;
	}

	@Override
	public void inject(Span span, SpanTextMap carrier) {
		Map<String, String> map = asMap(carrier);
		if (span == null) {
			if (!isSampled(map, TraceMessageHeaders.SAMPLED_NAME)) {
				carrier.put(TraceMessageHeaders.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED);
				return;
			}
			return;
		}
		addNewHeaders(span, carrier);
	}

	private boolean isSampled(Map<String, String> initialMessage, String sampledHeaderName) {
		return Span.SPAN_SAMPLED.equals(initialMessage.get(sampledHeaderName));
	}

	private void addNewHeaders(Span span, SpanTextMap initialMessage) {
		addHeaders(span, initialMessage, TraceMessageHeaders.TRACE_ID_NAME,
				TraceMessageHeaders.SPAN_ID_NAME, TraceMessageHeaders.PARENT_ID_NAME, TraceMessageHeaders.SPAN_NAME_NAME,
				TraceMessageHeaders.PROCESS_ID_NAME, TraceMessageHeaders.SAMPLED_NAME, TraceMessageHeaders.SPAN_HEADER);
	}

	private void addHeaders(Span span, SpanTextMap textMap, String traceIdHeader,
			String spanIdHeader, String parentIdHeader, String spanNameHeader, String processIdHeader,
			String spanSampledHeader, String spanHeader) {
		addHeader(textMap, traceIdHeader, Span.idToHex(span.getTraceId()));
		addHeader(textMap, spanIdHeader, Span.idToHex(span.getSpanId()));
		if (span.isExportable()) {
			addAnnotations(this.traceKeys, textMap, span);
			Long parentId = getFirst(span.getParents());
			if (parentId != null) {
				addHeader(textMap, parentIdHeader, Span.idToHex(parentId));
			}
			addHeader(textMap, spanNameHeader, span.getName());
			addHeader(textMap, processIdHeader, span.getProcessId());
			addHeader(textMap, spanSampledHeader, Span.SPAN_SAMPLED);
		}
		else {
			addHeader(textMap, spanSampledHeader, Span.SPAN_NOT_SAMPLED);
		}
	}

	private void addAnnotations(TraceKeys traceKeys, SpanTextMap spanTextMap, Span span) {
		Map<String, String> map = asMap(spanTextMap);
		for (String name : traceKeys.getMessage().getHeaders()) {
			if (map.containsKey(name)) {
				String key = traceKeys.getMessage().getPrefix() + name.toLowerCase();
				Object value = map.get(name);
				if (value == null) {
					value = "null";
				}
				// TODO: better way to serialize?
				tagIfEntryMissing(span, key, value.toString());
			}
		}
		addPayloadAnnotations(traceKeys, map, span);
	}

	private void addPayloadAnnotations(TraceKeys traceKeys, Map<String, String> map, Span span) {
		if (map.containsKey(traceKeys.getMessage().getPayload().getType())) {
			tagIfEntryMissing(span, traceKeys.getMessage().getPayload().getType(),
					map.get(traceKeys.getMessage().getPayload().getType()));
			tagIfEntryMissing(span, traceKeys.getMessage().getPayload().getSize(),
					map.get(traceKeys.getMessage().getPayload().getSize()));
		}
	}

	private void tagIfEntryMissing(Span span, String key, String value) {
		if (!span.tags().containsKey(key)) {
			span.tag(key, value);
		}
	}

	private void addHeader(SpanTextMap textMap, String name, String value) {
		if (StringUtils.hasText(value)) {
			textMap.put(name, value);
		}
	}

	private Long getFirst(List<Long> parents) {
		return parents.isEmpty() ? null : parents.get(0);
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
