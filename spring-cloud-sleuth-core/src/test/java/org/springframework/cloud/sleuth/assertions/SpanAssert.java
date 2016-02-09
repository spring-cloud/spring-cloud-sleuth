package org.springframework.cloud.sleuth.assertions;

import java.util.Objects;

import org.assertj.core.api.AbstractAssert;
import org.springframework.cloud.sleuth.Span;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpanAssert extends AbstractAssert<SpanAssert, Span> {

	public SpanAssert(Span actual) {
		super(actual, SpanAssert.class);
	}

	public static SpanAssert then(Span actual) {
		return new SpanAssert(actual);
	}

	public SpanAssert hasTraceIdEqualTo(Long traceId) {
		isNotNull();
		if (!Objects.equals(this.actual.getTraceId(), traceId)) {
			String message = String.format("Expected span's traceId to be <%s> but was <%s>", traceId, this.actual.getTraceId());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasNameNotEqualTo(String name) {
		isNotNull();
		if (Objects.equals(this.actual.getName(), name)) {
			String message = String.format("Expected span's name not to be <%s> but was <%s>", name, this.actual.getName());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasNameEqualTo(String name) {
		isNotNull();
		if (!Objects.equals(this.actual.getName(), name)) {
			String message = String.format("Expected span's name to be <%s> but it was <%s>", name, this.actual.getName());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}
}