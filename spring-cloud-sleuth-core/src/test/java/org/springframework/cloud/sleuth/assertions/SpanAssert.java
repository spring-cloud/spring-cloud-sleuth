package org.springframework.cloud.sleuth.assertions;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.AbstractAssert;
import org.springframework.cloud.sleuth.Span;

import java.util.Objects;

@Slf4j
public class SpanAssert extends AbstractAssert<SpanAssert, Span> {

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