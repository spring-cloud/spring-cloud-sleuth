package org.springframework.cloud.sleuth.zipkin;

/**
 * Contract for reporting Zipkin spans to Zipkin.
 *
 * @author Adrian Cole
 *
 * @since 1.0.0
 */
public interface ZipkinSpanReporter {
	/**
	 * Receives completed spans from {@link ZipkinSpanListener} and submits them to a Zipkin
	 * collector.
	 */
	void report(zipkin.Span span);
}
