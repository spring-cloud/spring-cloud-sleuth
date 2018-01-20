package org.springframework.cloud.sleuth.instrument.hystrix;

import brave.Tracer;
import brave.Tracing;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
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
@AutoConfigureAfter(TraceAutoConfiguration.class)
@ConditionalOnClass(HystrixCommand.class)
@ConditionalOnBean(Tracing.class)
@ConditionalOnProperty(value = "spring.sleuth.hystrix.strategy.enabled", matchIfMissing = true)
public class SleuthHystrixAutoConfiguration {

	@Bean SleuthHystrixConcurrencyStrategy sleuthHystrixConcurrencyStrategy(Tracer tracer,
			SpanNamer spanNamer, ErrorParser errorParser) {
		return new SleuthHystrixConcurrencyStrategy(tracer, spanNamer,
				errorParser);
	}

}
