package org.springframework.cloud.sleuth.metric;

import io.micrometer.core.instrument.Counter;

/**
 * Service to operate on accepted and dropped spans statistics.
 * Operates on a {@link Counter} underneath
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class CounterServiceBasedSpanMetricReporter implements SpanMetricReporter {
	private final Counter acceptedSpansCounter;
	private final Counter droppedSpansCounter;

	public CounterServiceBasedSpanMetricReporter(Counter acceptedSpansCounter,
			Counter droppedSpansCounter) {
		this.acceptedSpansCounter = acceptedSpansCounter;
		this.droppedSpansCounter = droppedSpansCounter;
	}

	@Override
	public void incrementAcceptedSpans(long quantity) {
		this.acceptedSpansCounter.increment(quantity);
	}

	@Override
	public void incrementDroppedSpans(long quantity) {
		this.droppedSpansCounter.increment(quantity);
	}
}