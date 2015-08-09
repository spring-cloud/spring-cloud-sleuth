/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(value = "spring.sleuth.schedule.enabled", matchIfMissing = true)
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
