package org.springframework.cloud.sleuth.correlation.scheduling

import org.springframework.cloud.sleuth.correlation.CorrelationIdHolder
import org.springframework.scheduling.annotation.Scheduled

class TestBeanWithScheduledMethod {

	String correlationId

	@Scheduled(fixedDelay = 50L)
	void scheduledMethod() {
		correlationId = CorrelationIdHolder.get()
	}

}
