package org.springframework.cloud.sleuth.zipkin;

import io.zipkin.Span;

public interface ZipkinSpanReporter {
  /**
   * Receives completed spans from {@link ZipkinSpanListener} and submits them to a Zipkin
   * collector.
   */
  void report(Span span);
}
