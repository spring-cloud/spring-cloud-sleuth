package org.springframework.cloud.sleuth.metric;

/**
 * Contract for a service that measures the number of accepted / dropped spans.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
public interface SpanMetricReporter {

	/**
	 * Called when spans are submitted to span collector for processing.
	 *
	 * @param quantity the number of spans accepted.
	 */
	void incrementAcceptedSpans(long quantity);

	/**
	 * Called when spans become lost for any reason and won't be delivered to the target collector.
	 *
	 * @param quantity the number of spans dropped.
	 */
	void incrementDroppedSpans(long quantity);
}
