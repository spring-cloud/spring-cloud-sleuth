package org.springframework.cloud.sleuth.instrument.scheduling;

/**
 * @author Spencer Gibb
 */

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Registers beans related to task scheduling.
 *
 * @see TraceSchedulingAspect
 *
 * @author Michal Chmielarz, 4financeIT
 * @author Spencer Gibb
 */
@Configuration
@EnableScheduling
@EnableAspectJAutoProxy
@ConditionalOnClass(ProceedingJoinPoint.class)
public class TraceSchedulingAutoConfiguration {

	@Bean
	public TraceSchedulingAspect traceSchedulingAspect(Trace trace) {
		return new TraceSchedulingAspect(trace);
	}
}
