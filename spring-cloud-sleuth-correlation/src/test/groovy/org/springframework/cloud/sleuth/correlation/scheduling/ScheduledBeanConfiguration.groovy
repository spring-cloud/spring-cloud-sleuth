package org.springframework.cloud.sleuth.correlation.scheduling

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ScheduledBeanConfiguration {

	@Bean
	TestBeanWithScheduledMethod testBeanWithScheduledMethod() {
		return new TestBeanWithScheduledMethod()
	}

}
