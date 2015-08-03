package org.springframework.cloud.sleuth.sample;

import com.github.kristofa.brave.LoggingSpanCollectorImpl;
import com.github.kristofa.brave.SpanCollector;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author Spencer Gibb
 */
@SpringBootApplication
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
	@Bean
	@ConditionalOnProperty("sleuth.sample.logging.collector.enabled")
	public SpanCollector spanCollector() {
		return new LoggingSpanCollectorImpl();
	}

}
