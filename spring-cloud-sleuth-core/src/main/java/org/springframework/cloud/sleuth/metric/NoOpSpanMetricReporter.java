package org.springframework.cloud.sleuth.metric;

/**
 * {@link SpanMetricReporter} that does nothing
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class NoOpSpanMetricReporter implements SpanMetricReporter {

	public void incrementAcceptedSpans(long quantity) {

	}

	public void incrementDroppedSpans(long quantity) {

	}
}