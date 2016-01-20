package org.springframework.cloud.sleuth.instrument.hystrix;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.hystrix.HystrixCommand;

@Configuration
@ConditionalOnClass(HystrixCommand.class)
@ConditionalOnProperty(value = "spring.sleuth.hystrix.strategy.enabled", matchIfMissing = true)
public class SleuthHystrixAutoConfiguration {

	@Bean SleuthHystrixConcurrencyStrategy sleuthHystrixConcurrencyStrategy(Tracer tracer) {
		return new SleuthHystrixConcurrencyStrategy(tracer);
	}
}
