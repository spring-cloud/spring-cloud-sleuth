package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.util.TextMapUtil;
import org.springframework.util.StringUtils;

/**
 * Default implementation for messaging
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public class HeaderBasedMessagingInjector implements MessagingSpanTextMapInjector {

	private final TraceKeys traceKeys;

	public HeaderBasedMessagingInjector(TraceKeys traceKeys) {
		this.traceKeys = traceKeys;
	}

	@Override
	public void inject(Span span, SpanTextMap carrier) {
		Map<String, String> map = TextMapUtil.asMap(carrier);
		if (span == null) {
			if (!isSampled(map, TraceMessageHeaders.SAMPLED_NAME)) {
				carrier.put(TraceMessageHeaders.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED);
				return;
			}
			return;
		}
		addHeaders(span, carrier);
	}

	private boolean isSampled(Map<String, String> initialMessage, String sampledHeaderName) {
		return Span.SPAN_SAMPLED.equals(initialMessage.get(sampledHeaderName));
	}

	private void addHeaders(Span span, SpanTextMap textMap) {
		addHeader(textMap, TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
		addHeader(textMap, TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
		if (span.isExportable()) {
			addAnnotations(this.traceKeys, textMap, span);
			Long parentId = getFirst(span.getParents());
			if (parentId != null) {
				addHeader(textMap, TraceMessageHeaders.PARENT_ID_NAME, Span.idToHex(parentId));
			}
			addHeader(textMap, TraceMessageHeaders.SPAN_NAME_NAME, span.getName());
			addHeader(textMap, TraceMessageHeaders.PROCESS_ID_NAME, span.getProcessId());
			addHeader(textMap, TraceMessageHeaders.SAMPLED_NAME, Span.SPAN_SAMPLED);
		}
		else {
			addHeader(textMap, TraceMessageHeaders.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED);
		}
	}

	private void addAnnotations(TraceKeys traceKeys, SpanTextMap spanTextMap, Span span) {
		Map<String, String> map = TextMapUtil.asMap(spanTextMap);
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

}
