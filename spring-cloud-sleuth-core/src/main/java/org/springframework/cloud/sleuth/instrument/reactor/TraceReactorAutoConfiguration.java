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

import javax.annotation.PreDestroy;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import brave.Tracing;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.sleuth.instrument.async.TraceableScheduledExecutorService;
import org.springframework.cloud.sleuth.instrument.web.TraceWebFluxAutoConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * to enable tracing of Reactor components via Spring Cloud Sleuth.
 *
 * @author Stephane Maldini
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnProperty(value="spring.sleuth.reactor.enabled", matchIfMissing=true)
@ConditionalOnClass(Mono.class)
@AutoConfigureAfter(TraceWebFluxAutoConfiguration.class)
public class TraceReactorAutoConfiguration {

	@Configuration
	@ConditionalOnBean(Tracing.class)
	static class TraceReactorConfiguration {

		static final String SLEUTH_TRACE_REACTOR_KEY = TraceReactorConfiguration.class.getName();

		@Bean
		@ConditionalOnNotWebApplication LastOperatorWrapper spanOperator() {
			return beanFactory -> Hooks.onLastOperator(SLEUTH_TRACE_REACTOR_KEY, ReactorSleuth.spanOperator(beanFactory));
		}

		@Bean
		@ConditionalOnWebApplication LastOperatorWrapper noOpLastOperatorWrapper() {
			return beanFactory -> { };
		}

		@PreDestroy
		public void cleanupHooks() {
			Hooks.resetOnLastOperator(SLEUTH_TRACE_REACTOR_KEY);
			Hooks.resetOnEachOperator(SLEUTH_TRACE_REACTOR_KEY);
			Schedulers.resetFactory();
		}

		@Bean
		// for tests
		@ConditionalOnMissingBean
		static HookRegisteringBeanDefinitionRegistryPostProcessor traceHookRegisteringBeanDefinitionRegistryPostProcessor() {
			return new HookRegisteringBeanDefinitionRegistryPostProcessor();
		}

		@Bean ApplicationContextRefreshedListener traceApplicationContextRefreshedListener() {
			return new ApplicationContextRefreshedListener();
		}
	}
}

interface LastOperatorWrapper {
	void wrapLastOperator(BeanFactory beanFactory);
}

class HookRegisteringBeanDefinitionRegistryPostProcessor implements
		BeanDefinitionRegistryPostProcessor {

	@Override public void postProcessBeanDefinitionRegistry(
			BeanDefinitionRegistry registry) throws BeansException {
	}

	@Override public void postProcessBeanFactory(
			ConfigurableListableBeanFactory beanFactory) throws BeansException {
		LastOperatorWrapper wrapper = beanFactory.getBean(LastOperatorWrapper.class);
		setupHooks(wrapper, beanFactory);
	}

	void setupHooks(LastOperatorWrapper wrapper, BeanFactory beanFactory) {
		wrapper.wrapLastOperator(beanFactory);
		Hooks.onEachOperator(
				TraceReactorAutoConfiguration.TraceReactorConfiguration.SLEUTH_TRACE_REACTOR_KEY,
				ReactorSleuth.scopePassingSpanOperator(beanFactory));
		Schedulers.setFactory(factoryInstance(beanFactory));
	}

	private Schedulers.Factory factoryInstance(final BeanFactory beanFactory) {
		return new Schedulers.Factory() {
			@Override public ScheduledExecutorService decorateExecutorService(String schedulerType,
					Supplier<? extends ScheduledExecutorService> actual) {
				return new TraceableScheduledExecutorService(beanFactory,
						actual.get());
			}
		};
	}
}

class ApplicationContextRefreshedListener implements
		ApplicationListener<ContextRefreshedEvent> {

	AtomicBoolean refreshed = new AtomicBoolean();

	@Override
	public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
		this.refreshed.set(true);
	}

	boolean isRefreshed() {
		return this.refreshed.get();
	}
}
