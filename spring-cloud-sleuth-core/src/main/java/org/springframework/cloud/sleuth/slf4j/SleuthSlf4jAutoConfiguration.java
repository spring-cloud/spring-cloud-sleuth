package org.springframework.cloud.sleuth.slf4j;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnClass(MDC.class)
public class SleuthSlf4jAutoConfiguration {

	@Bean
	public Slf4jSpanStartedListener slf4jSpanStartedListener() {
		return new Slf4jSpanStartedListener();
	}

	@Bean
	public Slf4jSpanStoppedListener slf4jSpanStoppedListener() {
		return new Slf4jSpanStoppedListener();
	}
}
