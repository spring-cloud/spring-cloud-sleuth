package org.springframework.cloud.sleuth.assertions;

import java.util.Objects;

import org.assertj.core.api.AbstractAssert;
import org.springframework.cloud.sleuth.Span;

public class SpanAssert extends AbstractAssert<SpanAssert, Span> {

  public SpanAssert(Span actual) {
    super(actual, SpanAssert.class);
  }

  public static SpanAssert then(Span actual) {
    return new SpanAssert(actual);
  }

  public SpanAssert hasTraceIdEqualTo(String traceId) {
    isNotNull();
    if (!Objects.equals(actual.getTraceId(), traceId)) {
      failWithMessage("Expected span's traceId to be <%s> but was <%s>", traceId, actual.getTraceId());
    }
    return this;
  }

  public SpanAssert hasNameNotEqualTo(String name) {
    isNotNull();
    if (Objects.equals(actual.getName(), name)) {
      failWithMessage("Expected span's name not to be <%s> but was <%s>", name, actual.getName());
    }
    return this;
  }
}