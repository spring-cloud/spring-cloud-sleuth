/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.reactor;

import javax.annotation.PreDestroy;

import brave.Tracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.instrument.async.TraceableScheduledExecutorService;
import org.springframework.cloud.sleuth.instrument.web.TraceWebFluxAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable tracing of Reactor components via Spring Cloud Sleuth.
 *
 * @author Stephane Maldini
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.reactor.enabled", matchIfMissing = true)
@ConditionalOnClass(Mono.class)
@AutoConfigureAfter(TraceWebFluxAutoConfiguration.class)
public class TraceReactorAutoConfiguration {

	static final String SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY = "sleuth";

	@Configuration
	@ConditionalOnBean(Tracing.class)
	static class TraceReactorConfiguration {

		static final String SLEUTH_TRACE_REACTOR_KEY = TraceReactorConfiguration.class
				.getName();

		private static final Log log = LogFactory.getLog(TraceReactorConfiguration.class);

		@Bean
		// for tests
		@ConditionalOnMissingBean
		static HookRegisteringBeanDefinitionRegistryPostProcessor traceHookRegisteringBeanDefinitionRegistryPostProcessor(
				ConfigurableApplicationContext context) {
			if (log.isTraceEnabled()) {
				log.trace(
						"Registering bean definition registry post processor for context ["
								+ context + "]");
			}
			return new HookRegisteringBeanDefinitionRegistryPostProcessor(context);
		}

		@PreDestroy
		public void cleanupHooks() {
			if (log.isTraceEnabled()) {
				log.trace("Cleaning up hooks");
			}
			Hooks.resetOnEachOperator(SLEUTH_TRACE_REACTOR_KEY);
			Schedulers
					.removeExecutorServiceDecorator(SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY);
		}

	}

}

class HookRegisteringBeanDefinitionRegistryPostProcessor
		implements BeanDefinitionRegistryPostProcessor {

	private final ConfigurableApplicationContext context;

	HookRegisteringBeanDefinitionRegistryPostProcessor(
			ConfigurableApplicationContext context) {
		this.context = context;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
			throws BeansException {
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
			throws BeansException {
		setupHooks(beanFactory);
	}

	void setupHooks(BeanFactory beanFactory) {
		Hooks.onEachOperator(
				TraceReactorAutoConfiguration.TraceReactorConfiguration.SLEUTH_TRACE_REACTOR_KEY,
				ReactorSleuth.scopePassingSpanOperator(this.context));
		Schedulers.setExecutorServiceDecorator(
				TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY,
				(scheduler,
						scheduledExecutorService) -> new TraceableScheduledExecutorService(
								beanFactory, scheduledExecutorService));
	}

}
