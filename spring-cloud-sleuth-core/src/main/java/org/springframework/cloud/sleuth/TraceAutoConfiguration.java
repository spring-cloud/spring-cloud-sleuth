package org.springframework.cloud.sleuth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.sleuth.sampler.IsTracingSampler;
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
	public Sampler defaultSampler() {
		return new IsTracingSampler();
	}

	@Bean
	@ConditionalOnMissingBean
	public Trace trace(Sampler sampler, IdGenerator idGenerator,
			ApplicationEventPublisher publisher) {
		return new DefaultTrace(sampler, idGenerator, publisher);
	}
}
