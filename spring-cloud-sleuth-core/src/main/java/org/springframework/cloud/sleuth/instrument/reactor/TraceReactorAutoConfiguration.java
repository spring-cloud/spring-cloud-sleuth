/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import javax.annotation.PreDestroy;

import brave.Tracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

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

	@Configuration
	@ConditionalOnBean(Tracing.class)
	static class TraceReactorConfiguration {

		private static final Log log = LogFactory.getLog(TraceReactorConfiguration.class);

		static final String SLEUTH_TRACE_REACTOR_KEY = TraceReactorConfiguration.class
				.getName();

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
			Schedulers.removeExecutorServiceDecorator(
					HookRegisteringBeanDefinitionRegistryPostProcessor.SLEUTH_DECORATOR
							.getAndSet(null));
		}

	}

}

class HookRegisteringBeanDefinitionRegistryPostProcessor
		implements BeanDefinitionRegistryPostProcessor {

	static final AtomicReference<BiFunction<Tuple2<String, String>, ScheduledExecutorService, ScheduledExecutorService>> SLEUTH_DECORATOR = new AtomicReference<>();

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
		BiFunction<Tuple2<String, String>, ScheduledExecutorService, ScheduledExecutorService> decorator = (
				objects,
				scheduledExecutorService) -> new TraceableScheduledExecutorService(
						beanFactory, scheduledExecutorService);
		SLEUTH_DECORATOR.set(decorator);
		Hooks.onEachOperator(
				TraceReactorAutoConfiguration.TraceReactorConfiguration.SLEUTH_TRACE_REACTOR_KEY,
				ReactorSleuth.scopePassingSpanOperator(this.context));
		Schedulers.addExecutorServiceDecorator(decorator);
	}

}