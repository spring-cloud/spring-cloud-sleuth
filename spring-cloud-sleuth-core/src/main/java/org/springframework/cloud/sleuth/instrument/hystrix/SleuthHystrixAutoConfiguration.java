package org.springframework.cloud.sleuth.instrument.hystrix;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.hystrix.HystrixCommand;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * that registers a custom Sleuth {@link com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 *
 * @see SleuthHystrixConcurrencyStrategy
 */
@Configuration
@ConditionalOnClass(HystrixCommand.class)
@ConditionalOnProperty(value = "spring.sleuth.hystrix.strategy.enabled", matchIfMissing = true)
public class SleuthHystrixAutoConfiguration {

	@Bean
	SleuthHystrixConcurrencyStrategy sleuthHystrixConcurrencyStrategy(Tracer tracer, TraceKeys traceKeys) {
		return new SleuthHystrixConcurrencyStrategy(tracer, traceKeys);
	}

}
