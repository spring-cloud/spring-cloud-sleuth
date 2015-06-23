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
	public Slf4jSpanStartListener slf4jSpanStartListener() {
		return new Slf4jSpanStartListener();
	}

	@Bean
	public Slf4jSpanReceiver slf4jSpanReceiver() {
		return new Slf4jSpanReceiver();
	}
}
