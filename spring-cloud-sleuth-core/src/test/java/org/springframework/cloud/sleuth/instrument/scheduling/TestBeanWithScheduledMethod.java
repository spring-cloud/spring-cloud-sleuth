package org.springframework.cloud.sleuth.instrument.scheduling;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContextHolder;
import org.springframework.scheduling.annotation.Scheduled;

class TestBeanWithScheduledMethod {

	Span span;

	@Scheduled(fixedDelay = 50L)
	public void scheduledMethod() {
		span = TraceContextHolder.getCurrentSpan();
	}

	public Span getSpan() {
		return span;
	}
}