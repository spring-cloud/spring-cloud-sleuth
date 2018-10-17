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

import brave.Tracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.instrument.async.TraceableScheduledExecutorService;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Replace default of onEachOperatorHook({@link ScopePassingSpanSubscriber}) to the {@link ScopePassingTraceContextSubscriber}.
 */
@Configuration
public class ScopePassingTraceContextHookConfig {
	@Bean
	public ScopePassingTraceContextHookRegisteringBeanDefinitionRegistryPostProcessor hookRegisteringBeanDefinitionRegistryPostProcessor(ConfigurableApplicationContext context) {
		return new ScopePassingTraceContextHookRegisteringBeanDefinitionRegistryPostProcessor(context);
	}

}

class ScopePassingTraceContextHookRegisteringBeanDefinitionRegistryPostProcessor extends HookRegisteringBeanDefinitionRegistryPostProcessor {

	private static final Log log = LogFactory.getLog(ScopePassingTraceContextHookRegisteringBeanDefinitionRegistryPostProcessor.class);
	private final ConfigurableApplicationContext context;

	ScopePassingTraceContextHookRegisteringBeanDefinitionRegistryPostProcessor(ConfigurableApplicationContext context) {
		super(context);
		this.context = context;
	}

	@Override
	void setupHooks( BeanFactory beanFactory) {
		Hooks.onEachOperator(
				TraceReactorAutoConfiguration.TraceReactorConfiguration.SLEUTH_TRACE_REACTOR_KEY,
				scopePassingSpanOperator(this.context));
		Schedulers.setFactory(factoryInstance(beanFactory));
	}


	private static <T> SpanSubscriptionProvider<T> scopePassingTraceContextSubscription(
			ConfigurableApplicationContext beanFactory, Scannable scannable, CoreSubscriber<? super T> sub) {
		return new SpanSubscriptionProvider<T>(
				beanFactory,
				sub,
				sub.currentContext(),
				scannable.name()) {
			@Override
			SpanSubscription newCoreSubscriber(Tracing tracing) {
				return new ScopePassingTraceContextSubscriber(
						sub,
						sub != null ? sub.currentContext() : Context.empty(),
						tracing);
			}
		};
	}

	private Schedulers.Factory factoryInstance(final BeanFactory beanFactory) {
		return new Schedulers.Factory() {
			@Override
			public ScheduledExecutorService decorateExecutorService(String schedulerType,
			                                                        Supplier<? extends ScheduledExecutorService> actual) {
				return new TraceableScheduledExecutorService(beanFactory,
						actual.get());
			}
		};
	}


	public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> scopePassingSpanOperator(
			ConfigurableApplicationContext beanFactory) {
		return sourcePub -> {
			// TODO: Remove this once Reactor 3.1.8 is released
			//do the checks directly on actual original Publisher
			if (sourcePub instanceof ConnectableFlux //Operators.lift can't handle that
					|| sourcePub instanceof GroupedFlux  //Operators.lift can't handle that
			) {
				return sourcePub;
			}
			//no more POINTCUT_FILTER since mechanism is broken
			Function<? super Publisher<T>, ? extends Publisher<T>> lift = Operators.lift((scannable, sub) -> {
				//rest of the logic unchanged...
				if (beanFactory.isActive()) {
					if (log.isTraceEnabled()) {
						log.trace("Spring Context already refreshed. Creating a scope " + "passing span subscriber with Reactor Context " + "[" + sub.currentContext() + "] and name [" + scannable.name() + "]");
					}
					return scopePassingTraceContextSubscription(beanFactory, scannable, sub).get();
				}
				if (log.isTraceEnabled()) {
					log.trace(
							"Spring Context is not yet refreshed, falling back to lazy span subscriber. Reactor Context is [" + sub.currentContext() + "] and name is [" + scannable.name() + "]");
				}
				return new LazySpanSubscriber<T>(
						scopePassingTraceContextSubscription(beanFactory, scannable, sub)
				);
			});

			return lift.apply(sourcePub);
		};
	}
}