package org.springframework.cloud.sleuth.instrument.web;

import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.cloud.sleuth.B3Utils;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.util.StringUtils;

/**
 * Default implementation, compatible with Zipkin propagation.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public class ZipkinHttpSpanExtractor implements HttpSpanExtractor, BeanFactoryAware {

	private static final org.apache.commons.logging.Log log = LogFactory.getLog(
			ZipkinHttpSpanExtractor.class);

	private static final String HTTP_COMPONENT = "http";

	private static final ZipkinHttpSpanMapper SPAN_CARRIER_MAPPER = new ZipkinHttpSpanMapper();

	private final Pattern skipPattern;
	private final Random random;
	private Sampler sampler;
	private BeanFactory beanFactory;

	/**
	 * @deprecated use {@link ZipkinHttpSpanExtractor#ZipkinHttpSpanExtractor(Pattern, Sampler)}
	 */
	@Deprecated
	public ZipkinHttpSpanExtractor(Pattern skipPattern) {
		this.skipPattern = skipPattern;
		this.random = new Random();
	}

	public ZipkinHttpSpanExtractor(Pattern skipPattern, Sampler sampler) {
		this.skipPattern = skipPattern;
		this.random = new Random();
		this.sampler = sampler;
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
		return traceId(carrier) == null;
	}

	private boolean spanIdIsPresent(Map<String, String> carrier) {
		return spanId(carrier) != null;
	}

	private String generateId() {
		return Span.idToHex(this.random.nextLong());
	}

	private long spanIdOrDefault(Map<String, String> carrier, String traceId) {
		String spanId = spanId(carrier);
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

	private String traceId(Map<String, String> carrier) {
		return B3Utils.traceId(Span.B3_NAME, Span.TRACE_ID_NAME, carrier);
	}

	private String spanId(Map<String, String> carrier) {
		return B3Utils.spanId(Span.B3_NAME, Span.SPAN_ID_NAME, carrier);
	}

	private B3Utils.Sampled sampled(Map<String, String> carrier) {
		return B3Utils.sampled(Span.B3_NAME,
				Span.SAMPLED_NAME, Span.SPAN_FLAGS, carrier);
	}

	private String traceIdOrDefaut(Map<String, String> carrier) {
		String traceId = traceId(carrier);
		if (traceId == null) {
			traceId = generateId();
		}
		return traceId;
	}

	private Span buildParentSpan(Map<String, String> carrier, boolean idToBeGenerated) {
		String traceId = traceIdOrDefaut(carrier);
		Span.SpanBuilder span = Span.builder()
				.traceIdHigh(traceId.length() == 32 ? Span.hexToId(traceId, 0) : 0)
				.traceId(Span.hexToId(traceId))
				.spanId(spanIdOrDefault(carrier, traceId));
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
		String parentId = B3Utils.parentSpanId(Span.B3_NAME, Span.PARENT_ID_NAME, carrier);
		if (parentId != null) {
			span.parent(Span.hexToId(parentId));
		}
		span.remote(true);
		B3Utils.Sampled sampled = sampled(carrier);
		boolean skip = this.skipPattern
				.matcher(carrier.get(ZipkinHttpSpanMapper.URI_HEADER)).matches()
				|| sampled == B3Utils.Sampled.NOT_SAMPLED;
		// trace, span id were retrieved from the headers and span is sampled
		span.shared(!(skip || idToBeGenerated));
		boolean debug = sampled == B3Utils.Sampled.DEBUG;
		for (Map.Entry<String, String> entry : carrier.entrySet()) {
			if (entry.getKey().toLowerCase()
					.startsWith(ZipkinHttpSpanMapper.BAGGAGE_PREFIX)) {
				span.baggage(unprefixedKey(entry.getKey()), entry.getValue());
			}
		}
		if (debug) {
			span.exportable(true);
		} else if (skip) {
			span.exportable(false);
		} else {
			span.exportable(sampled == null ?
					sampler().isSampled(span.build()) : sampled.isSampled());
		}
		return span.build();
	}

	private Sampler sampler() {
		// the new approach
		if (this.sampler != null) {
			return this.sampler;
		}
		// fallback not to break the API
		if (this.beanFactory != null) {
			this.sampler = this.beanFactory.getBean(Sampler.class);
		} else {
			// if somehow bean factory wasn't set it will behave as previously
			// this however should happen only in tests
			this.sampler = new AlwaysSampler();
		}
		return this.sampler;
	}

	private String unprefixedKey(String key) {
		return key.substring(key.indexOf(ZipkinHttpSpanMapper.HEADER_DELIMITER) + 1)
				.toLowerCase();
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}
}
