/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.interceptor.AsyncExecutionAspectSupport;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.brave.autoconfig.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.otel.autoconfig.OtelAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enabling async related processing.
 *
 * @author Dave Syer
 * @author Marcin Grzejszczak
 * @since 1.0.0
 * @see LazyTraceExecutor
 * @see TraceAsyncAspect
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SleuthAsyncProperties.class)
@ConditionalOnProperty(value = "spring.sleuth.async.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter({ BraveAutoConfiguration.class, OtelAutoConfiguration.class })
class AsyncDefaultAutoConfiguration {

	@Bean
	@ConditionalOnProperty(value = "spring.sleuth.scheduled.enabled", matchIfMissing = true)
	public static ExecutorBeanPostProcessor executorBeanPostProcessor(BeanFactory beanFactory) {
		return new ExecutorBeanPostProcessor(beanFactory);
	}

	@Bean
	public TraceAsyncAspect traceAsyncAspect(Tracer tracer, SpanNamer spanNamer) {
		return new TraceAsyncAspect(tracer, spanNamer);
	}

	/**
	 * Wrapper for the async executor.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(AsyncConfigurer.class)
	@ConditionalOnMissingBean(AsyncConfigurer.class)
	@ConditionalOnProperty(value = "spring.sleuth.async.configurer.enabled", matchIfMissing = true)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static class DefaultAsyncConfigurerSupport extends AsyncConfigurerSupport {

		private static final Log log = LogFactory.getLog(DefaultAsyncConfigurerSupport.class);

		@Autowired
		private BeanFactory beanFactory;

		@Override
		public Executor getAsyncExecutor() {
			Executor delegate = getDefaultExecutor();
			return new LazyTraceExecutor(this.beanFactory, delegate);
		}

		/**
		 * Retrieve or build a default executor for this advice instance. An executor
		 * returned from here will be cached for further use.
		 * <p>
		 * The default implementation searches for a unique {@link TaskExecutor} bean in
		 * the context, or for an {@link Executor} bean named "taskExecutor" otherwise. If
		 * neither of the two is resolvable, this implementation will return {@code null}.
		 * @return the default executor, or {@code null} if none available
		 * @see AsyncExecutionAspectSupport#getDefaultExecutor(org.springframework.beans.factory.BeanFactory)
		 */
		private Executor getDefaultExecutor() {
			try {
				// Search for TaskExecutor bean... not plain Executor since that would
				// match with ScheduledExecutorService as well, which is unusable for
				// our purposes here. TaskExecutor is more clearly designed for it.
				return this.beanFactory.getBean(TaskExecutor.class);
			}
			catch (NoUniqueBeanDefinitionException ex) {
				log.debug("Could not find unique TaskExecutor bean", ex);
				try {
					return this.beanFactory.getBean(AsyncExecutionAspectSupport.DEFAULT_TASK_EXECUTOR_BEAN_NAME,
							Executor.class);
				}
				catch (NoSuchBeanDefinitionException ex2) {
					if (log.isInfoEnabled()) {
						log.info("More than one TaskExecutor bean found within the context, and none is named "
								+ "'taskExecutor'. Mark one of them as primary or name it 'taskExecutor' (possibly "
								+ "as an alias) in order to use it for async processing: " + ex.getBeanNamesFound());
					}
				}
			}
			catch (NoSuchBeanDefinitionException ex) {
				log.debug("Could not find default TaskExecutor bean", ex);
				try {
					return this.beanFactory.getBean(AsyncExecutionAspectSupport.DEFAULT_TASK_EXECUTOR_BEAN_NAME,
							Executor.class);
				}
				catch (NoSuchBeanDefinitionException ex2) {
					log.info("No task executor bean found for async processing: "
							+ "no bean of type TaskExecutor and no bean named 'taskExecutor' either");
				}
				// Giving up -> either using local default executor or none at all...
			}
			// backward compatibility
			if (log.isInfoEnabled()) {
				log.info(
						"For backward compatibility, will fallback to the default, SimpleAsyncTaskExecutor implementation");
			}
			return new SimpleAsyncTaskExecutor();
		}

	}

}
