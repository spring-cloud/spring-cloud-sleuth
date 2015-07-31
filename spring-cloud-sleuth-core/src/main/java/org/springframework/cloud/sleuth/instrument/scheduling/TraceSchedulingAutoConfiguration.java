package org.springframework.cloud.sleuth.instrument.scheduling;

/**
 * @author Spencer Gibb
 */

import java.util.concurrent.Executor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Registers beans related to task scheduling.
 *
 * @see TraceSchedulingAspect
 *
 * @author Michal Chmielarz, 4financeIT
 * @author Spencer Gibb
 */
@Configuration
@EnableAspectJAutoProxy
public class TraceSchedulingAutoConfiguration {

	@ConditionalOnClass(ProceedingJoinPoint.class)
	@Bean
	public TraceSchedulingAspect traceSchedulingAspect(Trace trace) {
		return new TraceSchedulingAspect(trace);
	}

	@EnableAsync
	@Configuration
	protected static class AsyncConfiguration extends AsyncConfigurerSupport {

		@Autowired
		private Trace trace;

		// TODO: look for an existing AsyncConfigurer and steal its Executor
		@Override
		public Executor getAsyncExecutor() {
			return new TraceExecutor(this.trace, new SimpleAsyncTaskExecutor());
		}

	}

}
