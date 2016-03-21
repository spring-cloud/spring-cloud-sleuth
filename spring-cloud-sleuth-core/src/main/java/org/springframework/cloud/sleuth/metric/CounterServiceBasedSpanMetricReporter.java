package org.springframework.cloud.sleuth.metric;

import org.springframework.boot.actuate.metrics.CounterService;

/**
 * Service to operate on accepted and dropped spans statistics.
 * Operates on a {@link CounterService} underneath
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class CounterServiceBasedSpanMetricReporter implements SpanMetricReporter {
	private final String acceptedSpansMetricName;
	private final String droppedSpansMetricName;
	private final CounterService counterService;

	public CounterServiceBasedSpanMetricReporter(String acceptedSpansMetricName,
			String droppedSpansMetricName, CounterService counterService) {
		this.acceptedSpansMetricName = acceptedSpansMetricName;
		this.droppedSpansMetricName = droppedSpansMetricName;
		this.counterService = counterService;
	}

	@Override
	public void incrementAcceptedSpans(long quantity) {
		for (int i = 0; i < quantity; i++) {
			this.counterService.increment(this.acceptedSpansMetricName);
		}
	}

	@Override
	public void incrementDroppedSpans(long quantity) {
		for (int i = 0; i < quantity; i++) {
			this.counterService.increment(this.droppedSpansMetricName);
		}
	}
}