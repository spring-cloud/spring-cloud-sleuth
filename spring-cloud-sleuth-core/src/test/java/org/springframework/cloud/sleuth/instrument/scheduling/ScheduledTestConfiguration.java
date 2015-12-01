package org.springframework.cloud.sleuth.instrument.scheduling;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.netflix.archaius.ArchaiusAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.BaseConfigurationForITests;
import org.springframework.cloud.sleuth.instrument.integration.TraceSpringIntegrationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(BaseConfigurationForITests.class)
@EnableAutoConfiguration(exclude = {TraceSpringIntegrationAutoConfiguration.class, RefreshEndpointAutoConfiguration.class, ArchaiusAutoConfiguration.class})
class ScheduledTestConfiguration {

	@Bean TestBeanWithScheduledMethod testBeanWithScheduledMethod() {
		return new TestBeanWithScheduledMethod();
	}

}