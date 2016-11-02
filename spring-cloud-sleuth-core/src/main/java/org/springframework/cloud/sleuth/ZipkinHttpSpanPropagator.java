package org.springframework.cloud.sleuth;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.LogFactory;
import org.springframework.util.StringUtils;

/**
 * Default implementation, compatible with Zipkin propagation.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public class ZipkinHttpSpanPropagator implements HttpSpanExtractor, HttpSpanInjector {

	private static final org.apache.commons.logging.Log log = LogFactory.getLog(
			MethodHandles.lookup().lookupClass());
	static final String URI_HEADER = "X-Span-Uri";
	private static final String HTTP_COMPONENT = "http";

	@Override
	public void inject(Span span, SpanTextMap carrier) {
		setIdHeader(carrier, Span.TRACE_ID_NAME, span.getTraceId());
		setIdHeader(carrier, Span.SPAN_ID_NAME, span.getSpanId());
		setHeader(carrier, Span.SAMPLED_NAME, span.isExportable() ? Span.SPAN_SAMPLED : Span.SPAN_NOT_SAMPLED);
		setHeader(carrier, Span.SPAN_NAME_NAME, span.getName());
		setIdHeader(carrier, Span.PARENT_ID_NAME, getParentId(span));
		setHeader(carrier, Span.PROCESS_ID_NAME, span.getProcessId());
	}
	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
	}

	private void setHeader(SpanTextMap carrier, String name, String value) {
		if (StringUtils.hasText(value) && !entryPresent(carrier, name)) {
			carrier.put(name, value);
		}
	}

	private void setIdHeader(SpanTextMap carrier, String name, Long value) {
		if (value != null) {
			setHeader(carrier, name, Span.idToHex(value));
		}
	}

	private boolean entryPresent(SpanTextMap carrier, String name) {
		for (Map.Entry<String, String> entry : carrier) {
			if (entry.getKey().equals(name)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Span joinTrace(SpanTextMap textMap) {
		Map<String, String> carrier = asMap(textMap);
		if (carrier.get(Span.TRACE_ID_NAME) == null) {
			// can't build a Span without trace id
			return null;
		}
		try {
			boolean skip = Boolean.parseBoolean(carrier.get(Span.SAMPLED_NAME));
			long traceId = Span
					.hexToId(carrier.get(Span.TRACE_ID_NAME));
			long spanId = spanId(carrier, traceId);
			String uri = carrier.get(URI_HEADER);
			return buildParentSpan(carrier, uri, skip, traceId, spanId);
		} catch (Exception e) {
			log.error("Exception occurred while trying to extract span from carrier", e);
			return null;
		}
	}

	private long spanId(Map<String, String> carrier, long traceId) {
		String spanId = carrier.get(Span.SPAN_ID_NAME);
		if (spanId == null) {
			if (log.isDebugEnabled()) {
				log.debug("Request is missing a span id but it has a trace id. We'll assume that this is "
						+ "a root span with span id equal to trace id");
			}
			return traceId;
		} else {
			return Span.hexToId(spanId);
		}
	}

	private Span buildParentSpan(Map<String, String> carrier, String uri, boolean skip,
			long traceId, long spanId) {
		Span.SpanBuilder span = Span.builder().traceId(traceId).spanId(spanId);
		String processId = carrier.get(Span.PROCESS_ID_NAME);
		String parentName = carrier.get(Span.SPAN_NAME_NAME);
		if (StringUtils.hasText(parentName)) {
			span.name(parentName);
		}
		else {
			span.name(HTTP_COMPONENT + ":/parent" + uri);
		}
		if (StringUtils.hasText(processId)) {
			span.processId(processId);
		}
		if (carrier.containsKey(Span.PARENT_ID_NAME)) {
			span.parent(Span
					.hexToId(carrier.get(Span.PARENT_ID_NAME)));
		}
		span.remote(true);
		if (skip) {
			span.exportable(false);
		}
		return span.build();
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
