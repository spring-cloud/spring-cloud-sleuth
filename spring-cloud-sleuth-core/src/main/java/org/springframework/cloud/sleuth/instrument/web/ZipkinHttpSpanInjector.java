package org.springframework.cloud.sleuth.instrument.web;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link HttpSpanInjector}, compatible with Zipkin propagation.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public class ZipkinHttpSpanInjector implements HttpSpanInjector {

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
		if (StringUtils.hasText(value)) {
			carrier.put(name, value);
		}
	}

	private void setIdHeader(SpanTextMap carrier, String name, Long value) {
		if (value != null) {
			setHeader(carrier, name, Span.idToHex(value));
		}
	}

}
