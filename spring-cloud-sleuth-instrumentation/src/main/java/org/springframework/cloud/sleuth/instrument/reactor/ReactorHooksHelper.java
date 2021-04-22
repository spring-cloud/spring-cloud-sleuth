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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxOperator;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.core.publisher.Operators;
import reactor.core.publisher.ParallelFlux;
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
 */
final class ReactorHooksHelper {

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
				return true;
			}

			if (isSourceProducer(current)) {
				return false;
			}

			current = getParent(current);
		}
	}

	static boolean isTraceContextPropagator(Publisher<?> current) {
		return current instanceof TraceContextPropagator;
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
	 * Decorates {@link Publisher} with {@link TraceContextPropagator} {@code Publisher}.
	 * Mostly it is a copy of reactor-core logic from
	 * {@code reactor.core.publisher.Operators#liftPublisher()}.
	 * @param filter the Predicate that the raw Publisher must pass for the transformation
	 * to occur
	 * @param lifter the {@link BiFunction} taking the raw {@link Publisher} and
	 * {@link CoreSubscriber}. The function must return a receiving {@link CoreSubscriber}
	 * that will immediately subscribe to the {@link Publisher}.
	 * @param <O> the input and output type.
	 * @return a new {@link Function}.
	 */
	public static <O> Function<? super Publisher<O>, ? extends Publisher<O>> liftPublisher(Predicate<Publisher> filter,
			BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super O>> lifter) {
		Assert.notNull(lifter, "lifter is null");
		return publisher -> {
			if (filter != null && !filter.test(publisher)) {
				return (Publisher<O>) publisher;
			}

			if (publisher instanceof Mono) {
				return new SleuthMonoLift<>(publisher, lifter);
			}
			if (publisher instanceof ParallelFlux) {
				return new SleuthParallelLift<>((ParallelFlux<O>) publisher, lifter);
			}
			if (publisher instanceof ConnectableFlux) {
				return new SleuthConnectableLift<>((ConnectableFlux<O>) publisher, lifter);
			}
			if (publisher instanceof GroupedFlux) {
				return new SleuthGroupedLift<>((GroupedFlux<?, O>) publisher, lifter);
			}
			return new SleuthFluxLift<>(publisher, lifter);
		};
	}

}

/**
 * Copy of {@code reactor.core.publisher.MonoLift} which implements
 * {@link TraceContextPropagator}.
 */
final class SleuthMonoLift<I, O> extends MonoOperator<I, O> implements TraceContextPropagator {

	final BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super I>> lifter;

	SleuthMonoLift(Publisher<I> p,
			BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super I>> lifter) {
		super(Mono.from(p));
		this.lifter = lifter;
	}

	@Override
	public void subscribe(CoreSubscriber<? super O> actual) {
		CoreSubscriber input = actual;
		try {
			input = Objects.requireNonNull(lifter.apply(source, actual), "Lifted subscriber MUST NOT be null");

			source.subscribe(input);
		}
		catch (Throwable e) {
			Operators.reportThrowInSubscribe(input, e);
			return;
		}
	}

	@Override
	public Object scanUnsafe(Attr key) {
		if (key == Attr.RUN_STYLE) {
			return Attr.RunStyle.SYNC;
		}
		return super.scanUnsafe(key);
	}

}

/**
 * Copy of {@code reactor.core.publisher.FluxLift} which implements
 * {@link TraceContextPropagator}.
 */
final class SleuthFluxLift<I, O> extends FluxOperator<I, O> implements TraceContextPropagator {

	final BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super I>> lifter;

	SleuthFluxLift(Publisher<I> p,
			BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super I>> lifter) {
		super(Flux.from(p));
		this.lifter = lifter;
	}

	@Override
	public void subscribe(CoreSubscriber<? super O> actual) {
		CoreSubscriber input = actual;
		try {
			input = Objects.requireNonNull(lifter.apply(source, actual), "Lifted subscriber MUST NOT be null");

			source.subscribe(input);
		}
		catch (Throwable e) {
			Operators.reportThrowInSubscribe(input, e);
			return;
		}
	}

	@Override
	public Object scanUnsafe(Attr key) {
		if (key == Attr.RUN_STYLE) {
			return Attr.RunStyle.SYNC;
		}
		return super.scanUnsafe(key);
	}

}

/**
 * Copy of {@code reactor.core.publisher.ConnectableLift} which implements
 * {@link TraceContextPropagator}.
 */
final class SleuthConnectableLift<I, O> extends ConnectableFlux<O> implements Scannable, TraceContextPropagator {

	final ConnectableFlux<I> source;

	final BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super I>> lifter;

	SleuthConnectableLift(ConnectableFlux<I> p,
			BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super I>> lifter) {
		this.source = Objects.requireNonNull(p, "source");
		this.lifter = lifter;
	}

	@Override
	public int getPrefetch() {
		return source.getPrefetch();
	}

	@Override
	public void connect(Consumer<? super Disposable> cancelSupport) {
		this.source.connect(cancelSupport);
	}

	@Override
	@Nullable
	public Object scanUnsafe(Attr key) {
		if (key == Attr.PREFETCH) {
			return source.getPrefetch();
		}
		if (key == Attr.PARENT) {
			return source;
		}
		if (key == Attr.RUN_STYLE) {
			return Attr.RunStyle.SYNC;
		}
		return null;
	}

	@Override
	public void subscribe(CoreSubscriber<? super O> actual) {
		CoreSubscriber input = actual;
		try {
			input = Objects.requireNonNull(lifter.apply(source, actual), "Lifted subscriber MUST NOT be null");

			source.subscribe(input);
		}
		catch (Throwable e) {
			Operators.reportThrowInSubscribe(input, e);
			return;
		}
	}

}

/**
 * Copy of {@code reactor.core.publisher.GroupedLift} which implements
 * {@link TraceContextPropagator}.
 */
final class SleuthGroupedLift<K, I, O> extends GroupedFlux<K, O> implements Scannable, TraceContextPropagator {

	final BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super I>> lifter;

	final GroupedFlux<K, I> source;

	SleuthGroupedLift(GroupedFlux<K, I> p,
			BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super I>> lifter) {
		this.source = Objects.requireNonNull(p, "source");
		this.lifter = lifter;
	}

	@Override
	public int getPrefetch() {
		return source.getPrefetch();
	}

	@Override
	public K key() {
		return source.key();
	}

	@Override
	@Nullable
	public Object scanUnsafe(Attr key) {
		if (key == Attr.PARENT) {
			return source;
		}
		if (key == Attr.PREFETCH) {
			return getPrefetch();
		}
		if (key == Attr.RUN_STYLE) {
			return Attr.RunStyle.SYNC;
		}

		return null;
	}

	@Override
	public String stepName() {
		if (source instanceof Scannable) {
			return Scannable.from(source).stepName();
		}
		return Scannable.super.stepName();
	}

	@Override
	public void subscribe(CoreSubscriber<? super O> actual) {
		CoreSubscriber<? super I> input = lifter.apply(source, actual);

		Objects.requireNonNull(input, "Lifted subscriber MUST NOT be null");

		source.subscribe(input);
	}

}

/**
 * Copy of {@code reactor.core.publisher.ParallelLift} which implements
 * {@link TraceContextPropagator}.
 */
final class SleuthParallelLift<I, O> extends ParallelFlux<O> implements Scannable, TraceContextPropagator {

	final BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super I>> lifter;

	final ParallelFlux<I> source;

	SleuthParallelLift(ParallelFlux<I> p,
			BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super I>> lifter) {
		this.source = Objects.requireNonNull(p, "source");
		this.lifter = lifter;
	}

	@Override
	public int getPrefetch() {
		return source.getPrefetch();
	}

	@Override
	public int parallelism() {
		return source.parallelism();
	}

	@Override
	@Nullable
	public Object scanUnsafe(Attr key) {
		if (key == Attr.PARENT) {
			return source;
		}
		if (key == Attr.PREFETCH) {
			return getPrefetch();
		}
		if (key == Attr.RUN_STYLE) {
			return Attr.RunStyle.SYNC;
		}

		return null;
	}

	@Override
	public String stepName() {
		if (source instanceof Scannable) {
			return Scannable.from(source).stepName();
		}
		return Scannable.super.stepName();
	}

	@Override
	public void subscribe(CoreSubscriber<? super O>[] s) {
		@SuppressWarnings("unchecked")
		CoreSubscriber<? super I>[] subscribers = new CoreSubscriber[parallelism()];

		int i = 0;
		while (i < subscribers.length) {
			subscribers[i] = Objects.requireNonNull(lifter.apply(source, s[i]), "Lifted subscriber MUST NOT be null");
			i++;
		}

		source.subscribe(subscribers);
	}

}
