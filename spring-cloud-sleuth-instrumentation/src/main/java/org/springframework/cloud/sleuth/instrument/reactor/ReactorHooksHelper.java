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

package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.Objects;
import java.util.function.BiFunction;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.util.annotation.Nullable;

import org.springframework.util.Assert;

/**
 * Helper class for reactor ON_EACH instrumentation. Reduces number of sleuth operators
 * time in the reactive chain during Assembly by using {@code Scannable.Attr.RUN_STYLE}.
 * Below is the example of what it does <pre>{@code
 *	Mono.just(0) // (-)
 * 	.map(it -> 1) // (-)
 * 	.flatMap(it ->
 * 		Mono.just(it) // (-)
 * 	) // (-)
 * 	.flatMapMany(it ->
 * 		asyncPublisher(it) // (+)(*)
 * 	) // (-)
 * 	.filter(it -> true) // (-)
 * 	.publishOn(Schedulers.single()) //(+)
 * 	.map(it -> it) // (-)
 * 	.map(it -> it * 10) // (-)
 * 	.filter(it -> true) // (-)
 * 	.scan((l, r) -> l + r) // (-)
 * 	.doOnNext(it -> { // (-)
 * 		//log
 *    })
 * 	.doFirst(() -> { // (-)
 * 		//log
 *    })
 * 	.doFinally(signalType -> { // (-)
 * 		//log
 *    })
 * 	.subscribeOn(Schedulers.parallel()) //(+)
 * 	.subscribe();//(*)
 * 	(*) - captures tracing context if it differs from what was captured before at subscription and propagates it.
 *  As far as check is performed on Subscriber Context it allocates Publisher to access it.
 * 	(+) - is ASYNC need to decorate Publisher
 * 	(-) - is SYNC no need to decorate Publisher
 *}</pre> So it creates 13 (out of 16) less Publisher (+ Subscriber and all their stuff)
 * on assembly time comparing to the original logic (decorate every Publisher and checking
 * only at subscription time).
 * <p/>
 * It also handles the case when onEachHook was not applied for some operator in the chain
 * (It could be possible if custom operators or Processor are used) by checkin source
 * source Publisher. <pre>{@code
 * 	processor/customOperator // (?) is async and hook is not applied
 * 	.map(it -> ...) // (+) is SYNC but should add hook as previous Processor/operator does not use hooks
 * 	.doOnNext(it -> { // (-) is SYNC no need to wrap
 * 		//log
 *    })
 * 	.subscribe();
 *}</pre>
 *
 * @author Roman Matiushchenko
 */
final class ReactorHooksHelper {

	static final String LIFTER_NAME = "org.springframework.cloud.sleuth.instrument.reactor.ReactorHooksHelper.ScopePassingLifter";

	// need a way to determine SYNC sources to not add redundant scope passing decorator
	// most of reactor-core SYNC sources are marked with SourceProducer interface
	static final Class<?> sourceProducerClass;

	static {
		Class<?> c;
		try {
			c = Class.forName("reactor.core.publisher.SourceProducer");
		}
		catch (ClassNotFoundException e) {
			c = Void.class;
		}
		sourceProducerClass = c;
	}

	private ReactorHooksHelper() {
	}

	/**
	 * Determines whether to decorate input publisher with
	 * {@code ScopePassingSpanOperator}.
	 * @param p publisher to check
	 * @return returns true if input publisher or one of its source publishers is not
	 * {@code RunStyle.SYNC} and there is no {@link TraceContextPropagator} between input
	 * publisher and not SYNC publisher.
	 */
	public static boolean shouldDecorate(Publisher<?> p) {
		Assert.notNull(p, "source Publisher is null");
		Publisher<?> current = p;
		while (true) {
			if (current == null) {
				// is start of the chain, Publisher without source or foreign Publisher
				return true;
			}
			if (current instanceof Fuseable.ScalarCallable) {
				return false;
			}
			if (isTraceContextPropagator(current)) {
				return false;
			}

			if (!isSync(current)) {
				boolean isLifter = getLifterName(current) != null;
				if (isLifter) {
					return shouldDecorateLifter(current);
				}
				return true;
			}

			if (isSourceProducer(current)) {
				return false;
			}

			current = getParent(current);
		}
	}

	/**
	 * xxxLift Publishers get their RunStyle from source Publisher. So need to check
	 * whether current chain was decorated with scope passing operator.
	 * @param p first not sync lifter Publisher in the chain.
	 * @return {@code true} if Publisher chain does not contain lifter with
	 * {@link #LIFTER_NAME} name.
	 */
	private static boolean shouldDecorateLifter(Publisher<?> p) {
		Publisher<?> current = getParent(p);
		while (true) {
			if (current == null) {
				// is start of the chain, Publisher without source or foreign Publisher
				return true;
			}
			String lifterName = getLifterName(current);
			if (isScopePassingLifter(lifterName)) {
				return false;
			}
			if (lifterName == null) {
				return true;
			}
			current = getParent(current);
		}
	}

	private static boolean isScopePassingLifter(String lifterName) {
		return lifterName == LIFTER_NAME;
	}

	private static String getLifterName(Publisher<?> current) {
		return Scannable.from(current).scan(Scannable.Attr.LIFTER);
	}

	public static boolean isTraceContextPropagator(Publisher<?> current) {
		return current instanceof TraceContextPropagator || isScopePassingLifter(getLifterName(current));
	}

	private static boolean isSourceProducer(Publisher<?> p) {
		return sourceProducerClass.isInstance(p);
	}

	private static boolean isSync(Publisher<?> p) {
		return !(p instanceof Processor)
				&& Scannable.Attr.RunStyle.SYNC == Scannable.from(p).scan(Scannable.Attr.RUN_STYLE);
	}

	@Nullable
	private static Publisher<?> getParent(Publisher<?> publisher) {
		Object parent = Scannable.from(publisher).scanUnsafe(Scannable.Attr.PARENT);
		if (parent instanceof Publisher) {
			return (Publisher<?>) parent;
		}
		return null;
	}

	/**
	 * @param name function name.
	 * @param delegate delegate function.
	 * @param <T> the type of the first argument to the function
	 * @param <U> the type of the second argument to the function
	 * @param <R> the type of the result of the function
	 * @return function that {@link Object#toString()} returns provided name which is used
	 * as a value of {@link Scannable.Attr#LIFTER} attribute.
	 */
	static <T, U, R> BiFunction<T, U, R> named(String name, BiFunction<T, U, R> delegate) {
		return new NamedLifter<>(name, delegate);
	}

	static class NamedLifter<T, U, R> implements BiFunction<T, U, R> {

		private final BiFunction<T, U, R> delegate;

		private final String name;

		NamedLifter(String name, BiFunction<T, U, R> delegate) {
			this.name = Objects.requireNonNull(name, "name");
			this.delegate = Objects.requireNonNull(delegate, "delegate");
		}

		@Override
		public R apply(T t, U u) {
			return delegate.apply(t, u);
		}

		@Override
		public String toString() {
			return name;
		}

	}

}
