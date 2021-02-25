/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.instrument.reactor;

import java.io.Closeable;
import java.io.IOException;
import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.Queue;
import java.util.function.Function;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
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
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ReflectionUtils;

import static org.springframework.cloud.sleuth.autoconfig.instrument.reactor.TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY;
import static org.springframework.cloud.sleuth.autoconfig.instrument.reactor.TraceReactorAutoConfiguration.TraceReactorConfiguration.SLEUTH_TRACE_REACTOR_KEY;
import static org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth.onLastOperatorForOnEachInstrumentation;

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
@AutoConfigureAfter(name = "org.springframework.cloud.sleuth.autoconfig.instrument.web.TraceWebAutoConfiguration",
		value = BraveAutoConfiguration.class)
@EnableConfigurationProperties(SleuthReactorProperties.class)
public class TraceReactorAutoConfiguration {

	static final String SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY = "sleuth";

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBean(Tracer.class)
	static class TraceReactorConfiguration {

		static final String SLEUTH_TRACE_REACTOR_KEY = TraceReactorConfiguration.class.getName();

		private static final Log log = LogFactory.getLog(TraceReactorConfiguration.class);

		static final boolean IS_QUEUE_WRAPPER_ON_THE_CLASSPATH = isQueueWrapperOnTheClasspath();

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

		private static boolean isQueueWrapperOnTheClasspath() {
			return ReflectionUtils.findMethod(Hooks.class, "addQueueWrapper", String.class, Function.class) != null;
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
		Hooks.resetOnLastOperator(SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY);
		Hooks.removeQueueWrapper(SLEUTH_TRACE_REACTOR_KEY);
		switch (this.reactorProperties.getInstrumentationType()) {
		case DECORATE_QUEUES:
			if (TraceReactorAutoConfiguration.TraceReactorConfiguration.IS_QUEUE_WRAPPER_ON_THE_CLASSPATH) {
				if (log.isTraceEnabled()) {
					log.trace("Adding queue wrapper instrumentation");
				}
				HookRegisteringBeanDefinitionRegistryPostProcessor.addQueueWrapper(context);
			}
		case DECORATE_ON_EACH:
			if (log.isTraceEnabled()) {
				log.trace("Decorating onEach operator instrumentation");
			}
			Hooks.onEachOperator(SLEUTH_TRACE_REACTOR_KEY,
					ReactorSleuth.onEachOperatorForOnEachInstrumentation(this.context));
			Hooks.onLastOperator(SLEUTH_TRACE_REACTOR_KEY,
					ReactorSleuth.onLastOperatorForOnEachInstrumentation(this.context));
			Schedulers.onScheduleHook(TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY,
					ReactorSleuth.scopePassingOnScheduleHook(this.context));
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
				SleuthReactorProperties.InstrumentationType.DECORATE_QUEUES);
		if (wrapperNotOnClasspathHooksPropertyTurnedOn(property)) {
			log.warn(
					"You have explicitly set the decorate hooks option but you're using an old version of Reactor. Please upgrade to the latest Boot version (at least 2.4.3). Will fall back to the previous reactor instrumentation mode");
			property = SleuthReactorProperties.InstrumentationType.DECORATE_ON_EACH;
		}
		if (property == SleuthReactorProperties.InstrumentationType.DECORATE_QUEUES) {
			addQueueWrapper(springContext);
			decorateScheduler(springContext);
		}
		else {
			Boolean decorateOnEach = environment.getProperty("spring.sleuth.reactor.decorate-on-each", Boolean.class,
					true);
			if (!decorateOnEach) {
				log.warn(
						"You're using the deprecated [spring.sleuth.reactor.decorate-on-each] property. Please use the [spring.sleuth.reactor.instrumentation-type] one instead.");
				decorateOnLast(ReactorSleuth.scopePassingSpanOperator(springContext));
			}
			else if (property == SleuthReactorProperties.InstrumentationType.DECORATE_ON_EACH) {
				decorateOnEach(springContext);
				decorateOnLast(onLastOperatorForOnEachInstrumentation(springContext));
				decorateScheduler(springContext);
			}
			else if (property == SleuthReactorProperties.InstrumentationType.DECORATE_ON_LAST) {
				decorateOnLast(ReactorSleuth.scopePassingSpanOperator(springContext));
				decorateScheduler(springContext);
			}
			else if (property == SleuthReactorProperties.InstrumentationType.MANUAL) {
				decorateOnLast(ReactorSleuth.springContextSpanOperator(springContext));
			}
		}
	}

	private static boolean wrapperNotOnClasspathHooksPropertyTurnedOn(
			SleuthReactorProperties.InstrumentationType property) {
		return property == SleuthReactorProperties.InstrumentationType.DECORATE_QUEUES
				&& !TraceReactorAutoConfiguration.TraceReactorConfiguration.IS_QUEUE_WRAPPER_ON_THE_CLASSPATH;
	}

	private static void decorateScheduler(ConfigurableApplicationContext springContext) {
		Schedulers.onScheduleHook(TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY,
				ReactorSleuth.scopePassingOnScheduleHook(springContext));
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
		Hooks.onEachOperator(SLEUTH_TRACE_REACTOR_KEY,
				ReactorSleuth.onEachOperatorForOnEachInstrumentation(springContext));
	}

	static void addQueueWrapper(ConfigurableApplicationContext springContext) {
		if (log.isTraceEnabled()) {
			log.trace("Decorating queues");
		}
		Hooks.addQueueWrapper(SLEUTH_TRACE_REACTOR_KEY, queue -> traceQueue(springContext, queue));
	}

	@Override
	public void close() throws IOException {
		if (log.isTraceEnabled()) {
			log.trace("Cleaning up hooks");
		}
		Hooks.resetOnEachOperator(SLEUTH_TRACE_REACTOR_KEY);
		Hooks.resetOnLastOperator(SLEUTH_TRACE_REACTOR_KEY);
		Hooks.removeQueueWrapper(SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY);
		Schedulers.resetOnScheduleHook(TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY);
	}

	private static Queue<?> traceQueue(ConfigurableApplicationContext springContext, Queue<?> queue) {
		if (!springContext.isActive()) {
			return queue;
		}
		CurrentTraceContext currentTraceContext = springContext.getBean(CurrentTraceContext.class);
		@SuppressWarnings("unchecked")
		Queue envelopeQueue = queue;
		return new AbstractQueue<Object>() {

			@Override
			public int size() {
				return envelopeQueue.size();
			}

			@Override
			public boolean offer(Object o) {
				TraceContext traceContext = currentTraceContext.get();
				return envelopeQueue.offer(new Envelope(o, traceContext));
			}

			@Override
			public Object poll() {
				Object object = envelopeQueue.poll();
				if (object == null) {
					return null;
				}
				else if (object instanceof Envelope) {
					Envelope envelope = (Envelope) object;
					restoreTheContext(envelope);
					return envelope.body;
				}
				return object;
			}

			private void restoreTheContext(Envelope envelope) {
				if (envelope.traceContext != null) {
					currentTraceContext.maybeScope(envelope.traceContext);
				}
			}

			@Override
			public Object peek() {
				Object peek = queue.peek();
				if (peek instanceof Envelope) {
					Envelope envelope = (Envelope) peek;
					restoreTheContext(envelope);
					return (envelope).body;
				}
				return peek;
			}

			@Override
			@SuppressWarnings("unchecked")
			public Iterator<Object> iterator() {
				Iterator<?> iterator = queue.iterator();
				return new Iterator<Object>() {
					@Override
					public boolean hasNext() {
						return iterator.hasNext();
					}

					@Override
					public Object next() {
						Object next = iterator.next();
						if (next instanceof Envelope) {
							Envelope envelope = (Envelope) next;
							restoreTheContext(envelope);
							return (envelope).body;
						}
						return next;
					}
				};
			}
		};
	}

	static class Envelope {

		final Object body;

		final TraceContext traceContext;

		Envelope(Object body, TraceContext traceContext) {
			this.body = body;
			this.traceContext = traceContext;
		}

	}

}
