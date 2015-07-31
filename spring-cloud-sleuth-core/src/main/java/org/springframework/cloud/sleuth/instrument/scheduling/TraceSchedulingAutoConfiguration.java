package org.springframework.cloud.sleuth.instrument.scheduling;

/**
 * @author Spencer Gibb
 */

import java.util.concurrent.Executor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
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
	@ConditionalOnMissingBean(AsyncConfigurer.class)
	protected static class AsyncDefaultConfiguration extends AsyncConfigurerSupport {

		@Autowired
		private Trace trace;

		@Override
		public Executor getAsyncExecutor() {
			return new TraceExecutor(this.trace, new SimpleAsyncTaskExecutor());
		}

	}

	@Configuration
	@ConditionalOnBean(AsyncConfigurer.class)
	protected static class AsyncCustomConfiguration implements BeanPostProcessor {

		@Autowired
		private BeanFactory beanFactory;

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName)
				throws BeansException {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName)
				throws BeansException {
			if (bean instanceof AsyncConfigurer) {
				AsyncConfigurer configurer = (AsyncConfigurer) bean;
				return new LazyTraceAsyncCustomizer(this.beanFactory, configurer);
			}
			return bean;
		}

	}

}
