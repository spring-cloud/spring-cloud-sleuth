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

package org.springframework.cloud.sleuth.instrument.reactor;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.instrument.async.TraceableScheduledExecutorService;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfiguration.TraceReactorConfiguration.SLEUTH_TRACE_REACTOR_KEY;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable tracing of Reactor components via Spring Cloud Sleuth.
 *
 * @author Stephane Maldini
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.reactor.enabled", matchIfMissing = true)
@ConditionalOnClass(Mono.class)
@AutoConfigureAfter(name = "org.springframework.cloud.sleuth.instrument.web.TraceWebFluxConfiguration")
@EnableConfigurationProperties(SleuthReactorProperties.class)
class TraceReactorAutoConfiguration {

	static final String SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY = "sleuth";

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBean(Tracer.class)
	static class TraceReactorConfiguration {

		static final String SLEUTH_TRACE_REACTOR_KEY = TraceReactorConfiguration.class.getName();

		private static final Log log = LogFactory.getLog(TraceReactorConfiguration.class);

		@Autowired
		ConfigurableApplicationContext springContext;

		@Bean
		@ConditionalOnMissingBean
		HookRegisteringBeanDefinitionRegistryPostProcessor traceHookRegisteringBeanDefinitionRegistryPostProcessor(
				ConfigurableApplicationContext context) {
			if (log.isTraceEnabled()) {
				log.trace("Registering bean definition registry post processor for context [" + context + "]");
			}
			return new HookRegisteringBeanDefinitionRegistryPostProcessor(context);
		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnClass(RefreshScope.class)
		static class HooksRefresherConfiguration {

			@Bean
			HooksRefresher hooksRefresher(SleuthReactorProperties reactorProperties,
					ConfigurableApplicationContext context) {
				return new HooksRefresher(reactorProperties, context);
			}

		}

	}

}

class HooksRefresher implements ApplicationListener<RefreshScopeRefreshedEvent> {

	private static final Log log = LogFactory.getLog(HooksRefresher.class);

	private final SleuthReactorProperties reactorProperties;

	private final ConfigurableApplicationContext context;

	HooksRefresher(SleuthReactorProperties reactorProperties, ConfigurableApplicationContext context) {
		this.reactorProperties = reactorProperties;
		this.context = context;
	}

	@Override
	public void onApplicationEvent(RefreshScopeRefreshedEvent event) {
		if (log.isDebugEnabled()) {
			log.debug("Context refreshed, will reset hooks and then re-register them");
		}
		Hooks.resetOnEachOperator(SLEUTH_TRACE_REACTOR_KEY);
		Hooks.resetOnLastOperator(SLEUTH_TRACE_REACTOR_KEY);
		switch (this.reactorProperties.getInstrumentationType()) {
		case DECORATE_ON_EACH:
			if (log.isTraceEnabled()) {
				log.trace("Decorating onEach operator instrumentation");
			}
			Hooks.onEachOperator(SLEUTH_TRACE_REACTOR_KEY, ReactorSleuth.scopePassingSpanOperator(this.context));
			break;
		case DECORATE_ON_LAST:
			if (log.isTraceEnabled()) {
				log.trace("Decorating onLast operator instrumentation");
			}
			Hooks.onLastOperator(SLEUTH_TRACE_REACTOR_KEY, ReactorSleuth.scopePassingSpanOperator(this.context));
			break;
		case MANUAL:
			Hooks.onLastOperator(SLEUTH_TRACE_REACTOR_KEY, ReactorSleuth.springContextSpanOperator(this.context));
			break;
		}
	}

}

class HookRegisteringBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, Closeable {

	private static final Log log = LogFactory.getLog(HookRegisteringBeanDefinitionRegistryPostProcessor.class);

	final ConfigurableApplicationContext springContext;

	HookRegisteringBeanDefinitionRegistryPostProcessor(ConfigurableApplicationContext springContext) {
		this.springContext = springContext;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		setupHooks(this.springContext);
	}

	static void setupHooks(ConfigurableApplicationContext springContext) {
		ConfigurableEnvironment environment = springContext.getEnvironment();
		SleuthReactorProperties.InstrumentationType property = environment.getProperty(
				"spring.sleuth.reactor.instrumentation-type", SleuthReactorProperties.InstrumentationType.class,
				SleuthReactorProperties.InstrumentationType.DECORATE_ON_EACH);
		Boolean decorateOnEach = environment.getProperty("spring.sleuth.reactor.decorate-on-each", Boolean.class, true);
		if (!decorateOnEach) {
			log.warn(
					"You're using the deprecated [spring.sleuth.reactor.decorate-on-each] property. Please use the [spring.sleuth.reactor.instrumentation-type] one instead.");
			decorateOnLast(ReactorSleuth.scopePassingSpanOperator(springContext));
		}
		else if (property == SleuthReactorProperties.InstrumentationType.DECORATE_ON_EACH) {
			decorateOnEach(springContext);
		}
		else if (property == SleuthReactorProperties.InstrumentationType.DECORATE_ON_LAST) {
			decorateOnLast(ReactorSleuth.scopePassingSpanOperator(springContext));
		}
		else if (property == SleuthReactorProperties.InstrumentationType.MANUAL) {
			decorateOnLast(ReactorSleuth.springContextSpanOperator(springContext));
		}
		Schedulers.setExecutorServiceDecorator(TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY,
				(scheduler, scheduledExecutorService) -> new TraceableScheduledExecutorService(springContext,
						scheduledExecutorService));
	}

	private static void decorateOnLast(Function<? super Publisher<Object>, ? extends Publisher<Object>> function) {
		if (log.isTraceEnabled()) {
			log.trace("Decorating onLast operator instrumentation");
		}
		Hooks.onLastOperator(SLEUTH_TRACE_REACTOR_KEY, function);
	}

	private static void decorateOnEach(ConfigurableApplicationContext springContext) {
		if (log.isTraceEnabled()) {
			log.trace("Decorating onEach operator instrumentation");
		}
		Hooks.onEachOperator(SLEUTH_TRACE_REACTOR_KEY, ReactorSleuth.scopePassingSpanOperator(springContext));
	}

	@Override
	public void close() throws IOException {
		if (log.isTraceEnabled()) {
			log.trace("Cleaning up hooks");
		}
		Hooks.resetOnEachOperator(SLEUTH_TRACE_REACTOR_KEY);
		Hooks.resetOnLastOperator(SLEUTH_TRACE_REACTOR_KEY);
		Schedulers.removeExecutorServiceDecorator(TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY);
	}

}
