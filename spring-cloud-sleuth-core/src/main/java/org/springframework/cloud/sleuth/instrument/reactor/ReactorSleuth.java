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

import java.util.function.Function;

import brave.Tracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

/**
 * Reactive Span pointcuts factories
 *
 * @author Stephane Maldini
 * @since 2.0.0
 */
public abstract class ReactorSleuth {

	private static final Log log = LogFactory.getLog(ReactorSleuth.class);

	/**
	 * Return a span operator pointcut given a {@link BeanFactory}. This can be used in reactor
	 * via {@link reactor.core.publisher.Flux#transform(Function)}, {@link
	 * reactor.core.publisher.Mono#transform(Function)}, {@link
	 * reactor.core.publisher.Hooks#onEachOperator(Function)} or {@link
	 * reactor.core.publisher.Hooks#onLastOperator(Function)}.
	 *
	 * @param beanFactory
	 * @param <T> an arbitrary type that is left unchanged by the span operator
	 *
	 * @return a new lazy span operator pointcut
	 */
	@SuppressWarnings("unchecked")
	public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> spanOperator(
			BeanFactory beanFactory) {
		return sourcePub -> {
			// TODO: Remove this once Reactor 3.1.8 is released
			//do the checks directly on actual original Publisher
			if (sourcePub instanceof Fuseable //Sleuth can't handle that
					|| sourcePub instanceof Fuseable.ScalarCallable //IIRC Sleuth can't handle that
					|| sourcePub instanceof ConnectableFlux //Operators.lift can't handle that
					|| sourcePub instanceof GroupedFlux  //Operators.lift can't handle that
					) {
				return sourcePub;
			}
			//no more POINTCUT_FILTER since mechanism is broken
			Function<? super Publisher<T>, ? extends Publisher<T>> lift = Operators.lift((scannable, sub) -> {
				if (contextRefreshed(beanFactory)) {
					if (log.isTraceEnabled()) {
						log.trace("Spring Context already refreshed. Creating a Sleuth span subscriber with Reactor Context " + "[" + sub.currentContext() + "] and name [" + scannable.name() + "]");
					}
					return spanSubscriptionProvider(beanFactory, scannable, sub).get();
				}
				if (log.isTraceEnabled()) {
					log.trace(
							"Spring Context is not yet refreshed, falling back to lazy span subscriber. Reactor Context is [" + sub.currentContext() + "] and name is [" + scannable.name() + "]");
				}
				//rest of the logic unchanged...
				return new LazySpanSubscriber<T>(
						spanSubscriptionProvider(beanFactory, scannable, sub)
				);
			});
			return lift.apply(sourcePub);
		};
	}

	private static <T> SpanSubscriptionProvider spanSubscriptionProvider(
			BeanFactory beanFactory, Scannable scannable, CoreSubscriber<? super T> sub) {
		return new SpanSubscriptionProvider(
				beanFactory,
				sub,
				sub.currentContext(),
				scannable.name());
	}

	/**
	 * Return a span operator pointcut given a {@link Tracing}. This can be used in reactor
	 * via {@link reactor.core.publisher.Flux#transform(Function)}, {@link
	 * reactor.core.publisher.Mono#transform(Function)}, {@link
	 * reactor.core.publisher.Hooks#onEachOperator(Function)} or {@link
	 * reactor.core.publisher.Hooks#onLastOperator(Function)}. The Span operator
	 * pointcut will pass the Scope of the Span without ever creating any new spans.
	 *
	 * @param beanFactory
	 * @param <T> an arbitrary type that is left unchanged by the span operator
	 *
	 * @return a new lazy span operator pointcut
	 */
	@SuppressWarnings("unchecked")
	public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> scopePassingSpanOperator(
			BeanFactory beanFactory) {
		return sourcePub -> {
			// TODO: Remove this once Reactor 3.1.8 is released
			//do the checks directly on actual original Publisher
			if (sourcePub instanceof Fuseable //Sleuth can't handle that
					|| sourcePub instanceof Fuseable.ScalarCallable //IIRC Sleuth can't handle that
					|| sourcePub instanceof ConnectableFlux //Operators.lift can't handle that
					|| sourcePub instanceof GroupedFlux  //Operators.lift can't handle that
					) {
				return sourcePub;
			}
			//no more POINTCUT_FILTER since mechanism is broken
			Function<? super Publisher<T>, ? extends Publisher<T>> lift = Operators.lift((scannable, sub) -> {
				//rest of the logic unchanged...
				if (contextRefreshed(beanFactory)) {
					if (log.isTraceEnabled()) {
						log.trace("Spring Context already refreshed. Creating a scope " + "passing span subscriber with Reactor Context " + "[" + sub.currentContext() + "] and name [" + scannable.name() + "]");
					}
					return scopePassingSpanSubscription(beanFactory, scannable, sub).get();
				}
				if (log.isTraceEnabled()) {
					log.trace(
							"Spring Context is not yet refreshed, falling back to lazy span subscriber. Reactor Context is [" + sub.currentContext() + "] and name is [" + scannable.name() + "]");
				}
				return new LazySpanSubscriber<T>(
						scopePassingSpanSubscription(beanFactory, scannable, sub)
				);
			});

			return lift.apply(sourcePub);
		};
	}

	private static boolean contextRefreshed(BeanFactory beanFactory) {
		try {
			return beanFactory.getBean(ApplicationContextRefreshedListener.class).isRefreshed();
		} catch (NoSuchBeanDefinitionException e) {
			return false;
		}
	}

	private static <T> SpanSubscriptionProvider<T> scopePassingSpanSubscription(
			BeanFactory beanFactory, Scannable scannable, CoreSubscriber<? super T> sub) {
		return new SpanSubscriptionProvider<T>(
				beanFactory,
				sub,
				sub.currentContext(),
				scannable.name()) {
			@Override SpanSubscription newCoreSubscriber(Tracing tracing) {
				return new ScopePassingSpanSubscriber<T>(
						sub,
						sub != null ? sub.currentContext() : Context.empty(),
						tracing);
			}
		};
	}

	private ReactorSleuth() {
	}
}