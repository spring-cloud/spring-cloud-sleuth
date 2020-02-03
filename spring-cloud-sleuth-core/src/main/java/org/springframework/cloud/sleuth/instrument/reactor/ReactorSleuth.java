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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import org.springframework.context.ConfigurableApplicationContext;

/**
 * Reactive Span pointcuts factories.
 *
 * @author Stephane Maldini
 * @since 2.0.0
 */
public abstract class ReactorSleuth {

	private static final Log log = LogFactory.getLog(ReactorSleuth.class);

	private ReactorSleuth() {
	}

	/**
	 * Return a span operator pointcut given a {@link Tracing}. This can be used in
	 * reactor via {@link reactor.core.publisher.Flux#transform(Function)},
	 * {@link reactor.core.publisher.Mono#transform(Function)},
	 * {@link reactor.core.publisher.Hooks#onLastOperator(Function)} or
	 * {@link reactor.core.publisher.Hooks#onLastOperator(Function)}. The Span operator
	 * pointcut will pass the Scope of the Span without ever creating any new spans.
	 * @param springContext the Spring context.
	 * @param <T> an arbitrary type that is left unchanged by the span operator
	 * @return a new lazy span operator pointcut
	 */
	// Much of Boot assumes that the Spring context will be a
	// ConfigurableApplicationContext, rooted in SpringApplication's
	// requirement for it to be so. Previous versions of Reactor
	// instrumentation injected both BeanFactory and also
	// ConfigurableApplicationContext. This chooses the more narrow
	// signature as it is simpler than explaining instanceof checks.
	public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> scopePassingSpanOperator(
			ConfigurableApplicationContext springContext) {
		if (log.isTraceEnabled()) {
			log.trace("Scope passing operator [" + springContext + "]");
		}

		return Operators.liftPublisher((p, sub) -> {
			// While supply of scalar types may be deferred, we don't currently scope
			// production of values in a trace context. This prevents excessive overhead
			// when using constant results such as Flux/Mono #just, #empty, #error
			if (p instanceof Fuseable.ScalarCallable) {
				return sub;
			}

			if (!springContext.isActive()) {
				if (log.isTraceEnabled()) {
					log.trace("Spring Context [" + springContext
							+ "] is not yet refreshed. This is unexpected. Reactor Context is ["
							+ sub.currentContext() + "] and name is [" + name(sub) + "]");
				}
				assert false; // should never happen, but don't break.
				return sub;
			}

			if (log.isTraceEnabled()) {
				log.trace("Spring Context [" + springContext
						+ "] Creating a scope passing span subscriber with Reactor Context "
						+ "[" + sub.currentContext() + "] and name [" + name(sub) + "]");
			}

			return scopePassingSpanSubscription(springContext, sub);
		});
	}

	static String name(CoreSubscriber<?> sub) {
		return Scannable.from(sub).name();
	}

	private static Map<ConfigurableApplicationContext, CurrentTraceContext> CACHE = new ConcurrentHashMap<>();

	static <T> CoreSubscriber<? super T> scopePassingSpanSubscription(
			ConfigurableApplicationContext springContext, CoreSubscriber<? super T> sub) {
		CurrentTraceContext currentTraceContext = CACHE.computeIfAbsent(springContext,
				springContext1 -> springContext1.getBean(CurrentTraceContext.class));
		Context context = sub.currentContext();

		TraceContext parent = context.getOrDefault(TraceContext.class, null);
		if (parent == null) {
			parent = currentTraceContext.get();
		}
		if (parent != null) {
			return new ScopePassingSpanSubscriber<>(sub, context, currentTraceContext,
					parent);
		}
		else {
			return sub; // no need to trace
		}
	}

}
