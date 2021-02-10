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

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.Queue;

import javax.annotation.PreDestroy;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import org.springframework.cloud.sleuth.instrument.async.TraceableScheduledExecutorService;
import org.springframework.cloud.sleuth.instrument.web.TraceWebFluxAutoConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth.scopePassingSpanOperator;
import static org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfiguration.TraceReactorConfiguration.SLEUTH_TRACE_REACTOR_KEY;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable tracing of Reactor components via Spring Cloud Sleuth.
 *
 * @author Stephane Maldini
 * @author Marcin Grzejszczak
 * @since 2.0.0
 * @deprecated This type should have never been public and will be hidden or removed in
 * 3.0
 */
@Deprecated
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.reactor.enabled", matchIfMissing = true)
@ConditionalOnClass(Mono.class)
@AutoConfigureAfter(TraceWebFluxAutoConfiguration.class)
@EnableConfigurationProperties(SleuthReactorProperties.class)
public class TraceReactorAutoConfiguration {

	static final String SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY = "sleuth";

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBean(Tracing.class)
	static class TraceReactorConfiguration {

		static final String SLEUTH_TRACE_REACTOR_KEY = TraceReactorConfiguration.class
				.getName();

		private static final Log log = LogFactory.getLog(TraceReactorConfiguration.class);

		@Autowired
		ConfigurableApplicationContext springContext;

		@PreDestroy
		public void cleanupHooks() {
			if (log.isTraceEnabled()) {
				log.trace("Cleaning up hooks");
			}
			SleuthReactorProperties reactorProperties = this.springContext
					.getBean(SleuthReactorProperties.class);
			if (reactorProperties.isDecorateHooks()) {
				Hooks.removeQueueWrapper(SLEUTH_TRACE_REACTOR_KEY);
			}
			if (reactorProperties.isDecorateOnEach()) {
				if (log.isTraceEnabled()) {
					log.trace("Resetting onEach operator instrumentation");
				}
				Hooks.resetOnEachOperator(SLEUTH_TRACE_REACTOR_KEY);
			}
			else {
				if (log.isTraceEnabled()) {
					log.trace("Resetting onLast operator instrumentation");
				}
				Hooks.resetOnLastOperator(SLEUTH_TRACE_REACTOR_KEY);
			}
			Schedulers
					.removeExecutorServiceDecorator(SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY);
		}

		@Bean
		@ConditionalOnMissingBean
		HookRegisteringBeanDefinitionRegistryPostProcessor traceHookRegisteringBeanDefinitionRegistryPostProcessor(
				ConfigurableApplicationContext context) {
			if (log.isTraceEnabled()) {
				log.trace(
						"Registering bean definition registry post processor for context ["
								+ context + "]");
			}
			return new HookRegisteringBeanDefinitionRegistryPostProcessor(context);
		}

		@Configuration
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

	HooksRefresher(SleuthReactorProperties reactorProperties,
			ConfigurableApplicationContext context) {
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
		Hooks.removeQueueWrapper(SLEUTH_TRACE_REACTOR_KEY);
		if (this.reactorProperties.isDecorateHooks()) {
			HookRegisteringBeanDefinitionRegistryPostProcessor.addQueueWrapper(context);
		}
		if (this.reactorProperties.isDecorateOnEach()) {
			if (log.isTraceEnabled()) {
				log.trace("Decorating onEach operator instrumentation");
			}
			Hooks.onEachOperator(SLEUTH_TRACE_REACTOR_KEY,
					scopePassingSpanOperator(this.context));
		}
		else {
			if (log.isTraceEnabled()) {
				log.trace("Decorating onLast operator instrumentation");
			}
			Hooks.onLastOperator(SLEUTH_TRACE_REACTOR_KEY,
					scopePassingSpanOperator(this.context));
		}
	}

}

class HookRegisteringBeanDefinitionRegistryPostProcessor
		implements BeanDefinitionRegistryPostProcessor {

	private static final Log log = LogFactory
			.getLog(HookRegisteringBeanDefinitionRegistryPostProcessor.class);

	final ConfigurableApplicationContext springContext;

	HookRegisteringBeanDefinitionRegistryPostProcessor(
			ConfigurableApplicationContext springContext) {
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
		boolean decorateHooks = environment
				.getProperty("spring.sleuth.reactor.decorate-hooks", Boolean.class, true);
		if (decorateHooks) {
			addQueueWrapper(springContext);
		}
		else {
			boolean decorateOnEach = environment.getProperty(
					"spring.sleuth.reactor.decorate-on-each", Boolean.class, true);
			if (decorateOnEach) {
				if (log.isTraceEnabled()) {
					log.trace("Decorating onEach operator instrumentation");
				}
				Hooks.onEachOperator(SLEUTH_TRACE_REACTOR_KEY,
						scopePassingSpanOperator(springContext));
			}
			else {
				if (log.isTraceEnabled()) {
					log.trace("Decorating onLast operator instrumentation");
				}
				Hooks.onLastOperator(SLEUTH_TRACE_REACTOR_KEY,
						scopePassingSpanOperator(springContext));
			}
		}
		Schedulers.setExecutorServiceDecorator(
				TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY,
				(scheduler,
						scheduledExecutorService) -> new TraceableScheduledExecutorService(
								springContext, scheduledExecutorService));
	}

	static void addQueueWrapper(ConfigurableApplicationContext springContext) {
		Hooks.addQueueWrapper(SLEUTH_TRACE_REACTOR_KEY,
				queue -> traceQueue(springContext, queue));
	}

	private static Queue<?> traceQueue(ConfigurableApplicationContext springContext,
			Queue<?> queue) {
		if (!springContext.isActive()) {
			return queue;
		}
		Tracer tracer = springContext.getBean(Tracer.class);
		@SuppressWarnings("unchecked")
		Queue<Envelope> envelopeQueue = (Queue<Envelope>) queue;
		return new AbstractQueue<Object>() {

			@Override
			public int size() {
				return envelopeQueue.size();
			}

			@Override
			public boolean offer(Object o) {
				Span span = tracer.currentSpan();
				if (span != null) {
					// Clear the current thread
					tracer.withSpanInScope(null);
				}
				return envelopeQueue.offer(new Envelope(o, span));
			}

			@Override
			public Object poll() {
				Envelope envelope = envelopeQueue.poll();
				if (envelope == null) {
					return null;
				}
				if (envelope.span != null) {
					// puts in thread local
					tracer.withSpanInScope(envelope.span);
				}
				return envelope.body;
			}

			@Override
			public Object peek() {
				return queue.peek();
			}

			@Override
			@SuppressWarnings("unchecked")
			public Iterator<Object> iterator() {
				return (Iterator<Object>) queue.iterator();
			}
		};
	}

	static class Envelope {

		final Object body;

		final Span span;

		Envelope(Object body, Span span) {
			this.body = body;
			this.span = span;
		}

	}

}
