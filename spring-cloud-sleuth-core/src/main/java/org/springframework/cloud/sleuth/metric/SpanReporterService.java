package org.springframework.cloud.sleuth.metric;

/**
 * @author Marcin Grzejszczak
 */
public interface SpanReporterService {

	/**
	 * Called when spans are submitted to SpanCollector for processing.
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
