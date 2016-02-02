package org.springframework.cloud.sleuth.assertions;

import java.lang.invoke.MethodHandles;
import java.util.Objects;

import org.assertj.core.api.AbstractAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.sleuth.Span;

public class SpanAssert extends AbstractAssert<SpanAssert, Span> {

	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public SpanAssert(Span actual) {
		super(actual, SpanAssert.class);
	}

	public static SpanAssert then(Span actual) {
		return new SpanAssert(actual);
	}

	public SpanAssert hasTraceIdEqualTo(long traceId) {
		isNotNull();
		if (!Objects.equals(actual.getTraceId(), traceId)) {
			String message = String.format("Expected span's traceId to be <%s> but was <%s>", traceId, actual.getTraceId());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasNameNotEqualTo(String name) {
		isNotNull();
		if (Objects.equals(actual.getName(), name)) {
			String message = String.format("Expected span's name not to be <%s> but was <%s>", name, actual.getName());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}
}