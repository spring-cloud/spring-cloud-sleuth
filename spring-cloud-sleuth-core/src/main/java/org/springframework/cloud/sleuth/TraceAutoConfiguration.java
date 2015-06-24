package org.springframework.cloud.sleuth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.sleuth.sampler.IsTracingSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;

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
	public Sampler defaultSampler() {
		return new IsTracingSampler();
	}

	@Bean
	@ConditionalOnMissingBean
	public Trace trace(Sampler sampler, IdGenerator idGenerator, Collection<SpanStartListener> listeners, Collection<SpanReceiver> receivers) {
		return new DefaultTrace(sampler, idGenerator, listeners, receivers);
	}
}
