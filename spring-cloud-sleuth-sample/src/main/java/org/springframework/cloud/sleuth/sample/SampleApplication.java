package org.springframework.cloud.sleuth.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author Spencer Gibb
 */
@Configuration
@EnableAutoConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableAsync
public class SampleApplication {

	public static final String CLIENT_NAME = "testApp";

	@Bean
	public Sampler<?> defaultSampler() {
		return new AlwaysSampler();
	}

	@Bean
	public SampleController sampleController() {
		return new SampleController();
	}

	public static void main(String[] args) {
		SpringApplication.run(SampleApplication.class, args);
	}

	// Use this for debugging (or if there is no Zipkin collector running on port 9410)
	// @Bean
	// public SpanCollector spanCollector() {
	// return new LoggingSpanCollectorImpl();
	// }

}
