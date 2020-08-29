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
 * 	})
 * 	.doFirst(() -> { // (-)
 * 		//log
 * 	})
 * 	.doFinally(signalType -> { // (-)
 * 		//log
 * 	})
 * 	.subscribeOn(Schedulers.parallel()) //(+)
 * 	.subscribe();//(*)
 * 	(*) - captures brave context at subscription and propagates it
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
 * 	})
 * 	.subscribe();
 *}</pre>
 */
final class Hack {

	static final Class sourceProducerClass;
	static {
		Class c;
		try {
			c = Class.forName("reactor.core.publisher.SourceProducer");
		}
		catch (ClassNotFoundException e) {
			c = Void.class;
		}
		sourceProducerClass = c;
	}

	private Hack() {
	}

	static boolean shouldDecorate(Publisher<?> p) {
		int maxSteps = 100;
		Publisher<?> current = p;
		while (true) {
			if (current == null) {
				// is start of the chain, Publisher without source or foreign Publisher
				return true;
			}
			if (current instanceof Fuseable.ScalarCallable) {
				return false;
			}
			if (isDecorator(current)) {
				return false;
			}
			if (!isLifter(current)) {
				if (!isSync(current)) {
					return true;
				}

				if (isSourceProducer(current)) {
					return false;
				}
			}
			if (--maxSteps <= 0) {
				return true;
			}
			current = getParent(current);
		}
	}

	private static boolean isDecorator(Publisher<?> current) {
		return current instanceof SleuthDecorator;
	}

	private static boolean isSourceProducer(Publisher<?> p) {
		return sourceProducerClass.isInstance(p);
	}

	private static boolean isSync(Publisher<?> p) {
		return !(p instanceof Processor) && Scannable.Attr.RunStyle.SYNC == Scannable
				.from(p).scan(Scannable.Attr.RUN_STYLE);
	}

	@Nullable
	private static Publisher<?> getParent(Publisher<?> publisher) {
		Object parent = Scannable.from(publisher).scanUnsafe(Scannable.Attr.PARENT);
		if (parent instanceof Publisher) {
			return (Publisher<?>) parent;
		}
		return null;
	}

	static boolean isReactorCorePublisher(String pubClassName) {
		return pubClassName != null && pubClassName.startsWith("reactor.core.publisher");
	}

	// should be changed to Scannable. need extra Attr or interface for Lifter to
	// distinguish from others
	private static boolean isLifter(Publisher<?> current) {
		if (current == null) {
			return false;
		}
		String name = current.getClass().getName();
		return isReactorCorePublisher(name)
				&& ((name.endsWith("Lift") || name.endsWith("LiftFuseable")));
	}

	// WA need a way to determine whether Publisher is SleuthDecorator
	// mostly it is a copy of reactor-core logic
	// as on option - add Scannable to Lift Publishers which returns original lifter
	// function
	@SuppressWarnings("unchecked")
	public static <I, O> Function<? super Publisher<I>, ? extends Publisher<O>> liftPublisher(
			Predicate<Publisher> filter,
			BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super I>> lifter) {
		Function<? super Publisher<I>, ? extends Publisher<O>> coreLifter = Operators
				.liftPublisher(lifter);
		return publisher -> {
			if (filter != null && !filter.test(publisher)) {
				return (Publisher<O>) publisher;
			}

			if (publisher instanceof Mono) {
				return new SleuthMonoLift<>(publisher, lifter);
			}
			if (publisher instanceof ParallelFlux) {
				return new SleuthParallelLift<>((ParallelFlux<I>) publisher, lifter);
			}
			if (publisher instanceof ConnectableFlux) {
				return new SleuthConnectableLift<>((ConnectableFlux<I>) publisher,
						lifter);
			}
			if (publisher instanceof GroupedFlux) {
				return new SleuthGroupedLift<>((GroupedFlux<?, I>) publisher, lifter);
			}
			return new SleuthFluxLift<>(publisher, lifter);
		};
	}

}

final class SleuthMonoLift<I, O> extends MonoOperator<I, O> implements SleuthDecorator {

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
			input = Objects.requireNonNull(lifter.apply(source, actual),
					"Lifted subscriber MUST NOT be null");

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

final class SleuthFluxLift<I, O> extends FluxOperator<I, O> implements SleuthDecorator {

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
			input = Objects.requireNonNull(lifter.apply(source, actual),
					"Lifted subscriber MUST NOT be null");

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

final class SleuthConnectableLift<I, O> extends ConnectableFlux<O>
		implements Scannable, SleuthDecorator {

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
			input = Objects.requireNonNull(lifter.apply(source, actual),
					"Lifted subscriber MUST NOT be null");

			source.subscribe(input);
		}
		catch (Throwable e) {
			Operators.reportThrowInSubscribe(input, e);
			return;
		}
	}

}

final class SleuthGroupedLift<K, I, O> extends GroupedFlux<K, O>
		implements Scannable, SleuthDecorator {

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

final class SleuthParallelLift<I, O> extends ParallelFlux<O> implements Scannable {

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
			subscribers[i] = Objects.requireNonNull(lifter.apply(source, s[i]),
					"Lifted subscriber MUST NOT be null");
			i++;
		}

		source.subscribe(subscribers);
	}

}
