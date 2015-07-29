package org.springframework.cloud.sleuth.autoconfig;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.sleuth.IdGenerator;
import org.springframework.cloud.sleuth.RandomUuidGenerator;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.sampler.IsTracingSampler;
import org.springframework.cloud.sleuth.trace.DefaultTrace;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spencer Gibb
 */
@Configuration
public class TraceAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public IdGenerator idGenerator() {
		return new RandomUuidGenerator();
	}

	@Bean
	@ConditionalOnMissingBean
	public Sampler<Void> defaultSampler() {
		return new IsTracingSampler();
	}

	@Bean
	@ConditionalOnMissingBean
	public Trace trace(Sampler<Void> sampler, IdGenerator idGenerator,
			ApplicationEventPublisher publisher) {
		return new DefaultTrace(sampler, idGenerator, publisher);
	}
}
