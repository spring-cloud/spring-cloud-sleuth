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
import java.util.function.Predicate;

import brave.Tracing;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.core.publisher.Operators;
import org.reactivestreams.Publisher;
import reactor.util.context.Context;

/**
 * Reactive Span pointcuts factories
 *
 * @author Stephane Maldini
 * @since 2.0.0
 */
public abstract class ReactorSleuth {

	/**
	 * Return a span operator pointcut given a {@link Tracing}. This can be used in reactor
	 * via {@link reactor.core.publisher.Flux#transform(Function)}, {@link
	 * reactor.core.publisher.Mono#transform(Function)}, {@link
	 * reactor.core.publisher.Hooks#onEachOperator(Function)} or {@link
	 * reactor.core.publisher.Hooks#onLastOperator(Function)}.
	 *
	 * @param tracing the {@link Tracing} instance to use in this span operator
	 * @param <T> an arbitrary type that is left unchanged by the span operator
	 *
	 * @return a new Span operator pointcut
	 */
	public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> spanOperator(
			Tracing tracing) {
		return Operators.lift(POINTCUT_FILTER, ((scannable, sub) -> {
			//do not trace fused flows
			if(scannable instanceof Fuseable && sub instanceof Fuseable.QueueSubscription){
				return sub;
			}
			return new SpanSubscriber<>(
					sub,
					sub.currentContext(),
					tracing,
					scannable.name());
		}));
	}

	/**
	 * Return a span operator pointcut given a {@link Tracing}. This can be used in reactor
	 * via {@link reactor.core.publisher.Flux#transform(Function)}, {@link
	 * reactor.core.publisher.Mono#transform(Function)}, {@link
	 * reactor.core.publisher.Hooks#onEachOperator(Function)} or {@link
	 * reactor.core.publisher.Hooks#onLastOperator(Function)}. The Span operator
	 * pointcut will pass the Scope of the Span without ever creating any new spans.
	 *
	 * @param tracing the {@link Tracing} instance to use in this span operator
	 * @param <T> an arbitrary type that is left unchanged by the span operator
	 *
	 * @return a new Span operator pointcut
	 */
	public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> scopePassingSpanOperator(
			Tracing tracing) {
		return Operators.lift(POINTCUT_FILTER, ((scannable, sub) -> {
			//do not trace fused flows
			if(scannable instanceof Fuseable && sub instanceof Fuseable.QueueSubscription){
				return sub;
			}
			return new ScopePassingSpanSubscriber<>(
					sub,
					sub != null ? sub.currentContext() : Context.empty(),
					tracing);
		}));
	}

	private static final Predicate<Scannable> POINTCUT_FILTER =
			s ->  !(s instanceof Fuseable.ScalarCallable);

	private ReactorSleuth() {
	}
}
