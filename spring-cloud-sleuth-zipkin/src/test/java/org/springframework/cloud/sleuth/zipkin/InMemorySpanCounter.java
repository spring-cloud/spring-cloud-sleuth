package org.springframework.cloud.sleuth.zipkin;

import org.springframework.boot.actuate.metrics.CounterService;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of the metrics statistics held in-memory.
 *
 * @author Marcin Grzejszczak
 */
public class InMemorySpanCounter implements CounterService {

	private final AtomicLong acceptedSpans = new AtomicLong(0);
	private final AtomicLong droppedSpans = new AtomicLong(0);

	public long getAcceptedSpans() {
		return this.acceptedSpans.get();
	}

	public long getDroppedSpans() {
		return this.droppedSpans.get();
	}

	@Override
	public void increment(String metricName) {
		if (metricName.contains("accepted")) {
			this.acceptedSpans.incrementAndGet();
		} else {
			this.droppedSpans.incrementAndGet();
		}
	}

	@Override
	public void decrement(String metricName) {
		if (metricName.contains("accepted")) {
			this.acceptedSpans.decrementAndGet();
		} else {
			this.droppedSpans.decrementAndGet();
		}
	}

	@Override
	public void reset(String metricName) {
		this.acceptedSpans.set(0);
		this.droppedSpans.set(0);
	}
}
