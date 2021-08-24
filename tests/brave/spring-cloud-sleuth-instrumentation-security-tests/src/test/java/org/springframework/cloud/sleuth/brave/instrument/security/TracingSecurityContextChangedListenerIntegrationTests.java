package org.springframework.cloud.sleuth.brave.instrument.security;

import brave.sampler.Sampler;
import brave.test.IntegrationTestSpanHandler;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.brave.BraveTestSpanHandler;
import org.springframework.cloud.sleuth.instrument.security.SpringSecurityTests;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = TracingSecurityContextChangedListenerIntegrationTests.Config.class)
public class TracingSecurityContextChangedListenerIntegrationTests extends SpringSecurityTests {

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		TestSpanHandler testSpanHandler(brave.test.TestSpanHandler spanHandler) {
			return new BraveTestSpanHandler(spanHandler);
		}

		@Bean
		brave.test.TestSpanHandler spanHandler() {
			return new brave.test.TestSpanHandler();
		}
	}
}
