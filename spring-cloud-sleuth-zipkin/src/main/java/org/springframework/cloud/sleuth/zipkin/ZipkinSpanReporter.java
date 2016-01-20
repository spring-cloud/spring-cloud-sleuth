package org.springframework.cloud.sleuth.zipkin;

public interface ZipkinSpanReporter {
	/**
	 * Receives completed spans from {@link ZipkinSpanListener} and submits them to a Zipkin
	 * collector.
	 */
	void report(zipkin.Span span);
}
