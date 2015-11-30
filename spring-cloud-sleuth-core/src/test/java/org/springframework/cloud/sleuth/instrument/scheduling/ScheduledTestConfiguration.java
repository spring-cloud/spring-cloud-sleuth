package org.springframework.cloud.sleuth.instrument.scheduling;

import org.springframework.cloud.sleuth.instrument.BaseConfigurationForITests;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(BaseConfigurationForITests.class)
@DefaultTestAutoConfiguration
class ScheduledTestConfiguration {

	@Bean TestBeanWithScheduledMethod testBeanWithScheduledMethod() {
		return new TestBeanWithScheduledMethod();
	}

}