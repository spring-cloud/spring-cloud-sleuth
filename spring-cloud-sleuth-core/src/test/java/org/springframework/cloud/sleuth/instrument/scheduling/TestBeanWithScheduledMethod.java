package org.springframework.cloud.sleuth.instrument.scheduling;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.scheduling.annotation.Scheduled;

class TestBeanWithScheduledMethod {

	Span span;

	@Scheduled(fixedDelay = 1L)
	public void scheduledMethod() {
		this.span = TraceContextHolder.getCurrentSpan();
	}

	public Span getSpan() {
		return this.span;
	}
}