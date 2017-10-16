package org.springframework.cloud.sleuth.instrument.web;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.util.StringUtils;

/**
 * Default implementation, compatible with Zipkin propagation.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public class ZipkinHttpSpanExtractor implements HttpSpanExtractor {

	private static final org.apache.commons.logging.Log log = LogFactory.getLog(
			MethodHandles.lookup().lookupClass());

	private static final String HTTP_COMPONENT = "http";

	private static final ZipkinHttpSpanMapper SPAN_CARRIER_MAPPER = new ZipkinHttpSpanMapper();

	private final Pattern skipPattern;
	private final Random random;

	public ZipkinHttpSpanExtractor(Pattern skipPattern) {
		this.skipPattern = skipPattern;
		this.random = new Random();
	}

	@Override
	public Span joinTrace(SpanTextMap textMap) {
		Map<String, String> carrier = SPAN_CARRIER_MAPPER.convert(textMap);

		boolean debug = Span.SPAN_SAMPLED.equals(carrier.get(Span.SPAN_FLAGS));
		boolean idToBeGenerated = debug && onlySpanIdIsPresent(carrier);
		// we're only generating Trace ID since if there's no Span ID will assume
		// that it's equal to Trace ID - we're trying to fix a malformed request
		if (!idToBeGenerated && traceIdIsMissing(carrier)) {
			// can't build a Span without trace id
			return null;
		}
		try {
			return buildParentSpan(carrier, idToBeGenerated);
		} catch (Exception e) {
			log.error("Exception occurred while trying to extract span from carrier", e);
			return null;
		}
	}

	private boolean onlySpanIdIsPresent(Map<String, String> carrier) {
		return traceIdIsMissing(carrier) && spanIdIsPresent(carrier);
	}

	private boolean traceIdIsMissing(Map<String, String> carrier) {
		return carrier.get(Span.TRACE_ID_NAME) == null;
	}

	private boolean spanIdIsPresent(Map<String, String> carrier) {
		return carrier.get(Span.SPAN_ID_NAME) != null;
	}

	private String generateId() {
		return Span.idToHex(this.random.nextLong());
	}

	private long spanId(String spanId, String traceId) {
		if (spanId == null) {
			if (log.isDebugEnabled()) {
				log.debug("Request is missing a span id but it has a trace id. We'll assume that this is "
						+ "a root span with span id equal to the lower 64-bits of the trace id");
			}
			return Span.hexToId(traceId);
		} else {
			return Span.hexToId(spanId);
		}
	}

	private Span buildParentSpan(Map<String, String> carrier, boolean idToBeGenerated) {
		String traceId = carrier.get(Span.TRACE_ID_NAME);
		if (traceId == null) {
			traceId = generateId();
		}
		Span.SpanBuilder span = Span.builder()
				.traceIdHigh(traceId.length() == 32 ? Span.hexToId(traceId, 0) : 0)
				.traceId(Span.hexToId(traceId))
				.spanId(spanId(carrier.get(Span.SPAN_ID_NAME), traceId));
		String parentName = carrier.get(Span.SPAN_NAME_NAME);
		if (StringUtils.hasText(parentName)) {
			span.name(parentName);
		}  else {
			span.name(HTTP_COMPONENT + ":/parent"
					+ carrier.get(ZipkinHttpSpanMapper.URI_HEADER));
		}
		String processId = carrier.get(Span.PROCESS_ID_NAME);
		if (StringUtils.hasText(processId)) {
			span.processId(processId);
		}
		String parentId = carrier.get(Span.PARENT_ID_NAME);
		if (parentId != null) {
			span.parent(Span.hexToId(parentId));
		}
		span.remote(true);

		boolean skip = this.skipPattern
				.matcher(carrier.get(ZipkinHttpSpanMapper.URI_HEADER)).matches()
				|| Span.SPAN_NOT_SAMPLED.equals(carrier.get(Span.SAMPLED_NAME));
		// trace, span id were retrieved from the headers and span is sampled
		span.shared(!(skip || idToBeGenerated));
		boolean debug = Span.SPAN_SAMPLED.equals(carrier.get(Span.SPAN_FLAGS));
		if (debug) {
			span.exportable(true);
		} else if (skip) {
			span.exportable(false);
		}
		for (Map.Entry<String, String> entry : carrier.entrySet()) {
			if (entry.getKey().toLowerCase()
					.startsWith(ZipkinHttpSpanMapper.BAGGAGE_PREFIX)) {
				span.baggage(unprefixedKey(entry.getKey()), entry.getValue());
			}
		}
		return span.build();
	}

	private String unprefixedKey(String key) {
		return key.substring(key.indexOf(ZipkinHttpSpanMapper.HEADER_DELIMITER) + 1)
				.toLowerCase();
	}

}
