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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.Scannable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.core.publisher.Operators;
import reactor.core.publisher.ParallelFlux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.util.annotation.Nullable;
import reactor.util.context.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ReactorHooksHelperTests {

	@BeforeEach
	public void setUp() {
		Hooks.resetOnEachOperator();
		Hooks.resetOnLastOperator();
		Schedulers.resetOnScheduleHooks();
	}

	@Test
	public void shouldDecorateWhenSyncSourceThanShouldNotDecorate() {
		boolean actual = ReactorHooksHelper.shouldDecorate(Mono.just(1).map(Function.identity()));
		assertThat(actual).isFalse();
	}

	@Test
	public void shouldDecorateWhenSyncSourceProducerThanShouldNotDecorate() {
		// Mono.fromSupplier() is instance of SourceProduced and is not
		// Fuseable.ScalarCallable like Mono.just()
		Mono<Integer> syncSource = Mono.fromSupplier(() -> 1);
		boolean actual = ReactorHooksHelper.shouldDecorate(syncSource.map(Function.identity()));
		assertThat(actual).isFalse();
	}

	@Test
	public void shouldDecorateWhenNotSyncSourceProducerThanShouldDecorate() {
		Mono<Long> asyncSource = Mono.delay(Duration.ofMillis(10));
		boolean actual = ReactorHooksHelper.shouldDecorate(asyncSource.map(Function.identity()));
		assertThat(actual).isTrue();
	}

	@Test
	public void shouldDecorateWhenProcessorSourceThanShouldNotDecorate() {
		Mono<Long> asyncSource = Sinks.<Long>one().asMono();
		boolean actual = ReactorHooksHelper.shouldDecorate(asyncSource.map(Function.identity()));
		assertThat(actual).isTrue();
	}

	@Test
	public void shouldDecorateWhenAsyncSourceAndLifterThanShouldDecorate() {
		Mono<?> asyncSourceWithLifter = Mono.delay(Duration.ofMillis(10)).transform(Operators.liftPublisher(
				new BiFunction<Publisher, CoreSubscriber<? super Object>, CoreSubscriber<? super Long>>() {
					@Override
					public CoreSubscriber<? super Long> apply(Publisher pub, CoreSubscriber<? super Object> sub) {
						return sub;
					}
				}));
		boolean actual = ReactorHooksHelper.shouldDecorate(asyncSourceWithLifter.map(Function.identity()));
		assertThat(actual).isTrue();
	}

	@Test
	public void shouldDecorateWhenSyncSourceAndLifterThanShouldNotDecorate() {
		Mono<?> syncSourceWithLifter = Mono.fromSupplier(() -> 1L).transform(Operators.liftPublisher(
				new BiFunction<Publisher, CoreSubscriber<? super Object>, CoreSubscriber<? super Long>>() {
					@Override
					public CoreSubscriber<? super Long> apply(Publisher pub, CoreSubscriber<? super Object> sub) {
						return sub;
					}
				}));
		boolean actual = ReactorHooksHelper.shouldDecorate(syncSourceWithLifter.map(Function.identity()));
		assertThat(actual).isFalse();
	}

	@Test
	public void shouldDecorateWhenAsyncSourceAndTraceContextPropagatorThanShouldNotDecorate() {
		Function<? super Publisher<Long>, ? extends Publisher<Long>> traceContextPropagator = ReactorHooksHelper
				.liftPublisher(publisher -> true, (publisher, coreSubscriber) -> coreSubscriber);
		Mono<?> syncSourceWithLifter = Mono.delay(Duration.ofMillis(10)).transform(traceContextPropagator);
		boolean actual = ReactorHooksHelper.shouldDecorate(syncSourceWithLifter.map(Function.identity()));
		assertThat(actual).isFalse();
	}

	@Test
	public void shouldDecorateWhenNullSourceProducerThanError() {
		assertThatCode(() -> ReactorHooksHelper.shouldDecorate(null)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("source Publisher is null");
	}

	@Test
	public void shouldDecorateWhenOperatorWithoutRunStyleThanShouldDecorate() {
		Mono<?> source = Mono.just(1).as(CustomMonoWithoutRunStyleOperator::new);
		boolean actual = ReactorHooksHelper.shouldDecorate(source.map(Function.identity()));
		assertThat(actual).isTrue();
	}

	@Test
	public void liftPublisherWhenFilterDiscardsThenReturnSource() {
		Mono<Integer> source = Mono.just(1);
		final Function<? super Publisher<Integer>, ? extends Publisher<Integer>> transformer = ReactorHooksHelper
				.liftPublisher(p -> false, (p, s) -> s);
		Mono<Integer> transformed = source.transform(transformer);
		assertThat(source).isSameAs(transformed);
	}

	@Test
	public void liftPublisherWhenFilterIsNullThenDecorate() {
		Mono<Integer> source = Mono.just(1);
		final Function<? super Publisher<Integer>, ? extends Publisher<Integer>> transformer = ReactorHooksHelper
				.liftPublisher(null, (p, s) -> s);
		Mono<Integer> transformed = source.transform(transformer);
		assertThat(source).isNotSameAs(transformed);
		assertThat(transformed).isInstanceOf(SleuthMonoLift.class);
	}

	@Test
	public void liftPublisherWhenSourceMonoThenDecorateWithMono() {
		Mono<Integer> source = Mono.just(1);
		final Function<? super Publisher<Integer>, ? extends Publisher<Integer>> transformer = ReactorHooksHelper
				.liftPublisher(p -> true, (p, s) -> new PlusOneSubscriber(s));
		Mono<Integer> transformed = source.transform(transformer);
		assertThat(source).isNotSameAs(transformed);
		assertThat(transformed).isInstanceOf(SleuthMonoLift.class).isInstanceOf(TraceContextPropagator.class);

		StepVerifier.create(transformed).expectSubscription().expectNext(2).expectComplete()
				.verify(Duration.ofSeconds(5));
	}

	@Test
	public void liftPublisherWhenSourceFluxThenDecorateWithFlux() {
		Flux<Integer> source = Flux.just(1, 2, 3);
		final Function<? super Publisher<Integer>, ? extends Publisher<Integer>> transformer = ReactorHooksHelper
				.liftPublisher(p -> true, (p, s) -> new PlusOneSubscriber(s));
		Flux<Integer> transformed = source.transform(transformer);
		assertThat(source).isNotSameAs(transformed);
		assertThat(transformed).isInstanceOf(SleuthFluxLift.class).isInstanceOf(TraceContextPropagator.class);

		StepVerifier.create(transformed).expectSubscription().expectNext(2).expectNext(3).expectNext(4).expectComplete()
				.verify(Duration.ofSeconds(5));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void liftPublisherWhenSourceParallelFluxThenDecorateWithParallelFlux() {
		ParallelFlux<Integer> source = Flux.just(1, 2, 3).parallel();

		Function function = ReactorHooksHelper.liftPublisher(p -> true,
				(p, s) -> (CoreSubscriber) new PlusOneSubscriber(s));

		ParallelFlux<Integer> transformed = source.transform(function);
		assertThat(source).isNotSameAs(transformed);
		assertThat(transformed).isInstanceOf(SleuthParallelLift.class).isInstanceOf(TraceContextPropagator.class);
		Flux<Integer> sorted = transformed.map(it -> it).sequential().sort();
		StepVerifier.create(sorted).expectSubscription().expectNext(2).expectNext(3).expectNext(4).expectComplete()
				.verify(Duration.ofSeconds(5));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void liftPublisherWhenSourceConnectableFluxThenDecorateWithConnectableFlux() {
		final AtomicBoolean sourceCanceled = new AtomicBoolean();
		ConnectableFlux<Integer> source = Flux.just(1, 2, 3).doOnCancel(() -> sourceCanceled.set(true)).replay();

		Function function = ReactorHooksHelper.liftPublisher(p -> true,
				(p, s) -> (CoreSubscriber) new PlusOneSubscriber(s));

		Flux<Integer> transformed = source.transform(function);
		assertThat(source).isNotSameAs(transformed);
		assertThat(transformed).isInstanceOf(SleuthConnectableLift.class).isInstanceOf(TraceContextPropagator.class);
		final AtomicReference<Disposable> cancelSource = new AtomicReference<>();
		Flux<Integer> autoConnect = ((ConnectableFlux<Integer>) transformed).autoConnect(1, cancelSource::set);

		StepVerifier.create(autoConnect).expectSubscription().expectNext(2).expectNext(3).expectNext(4).thenCancel()
				.verify(Duration.ofSeconds(5));

		assertThat(cancelSource.get()).isNotNull();
		cancelSource.get().dispose();
		assertThat(sourceCanceled).isTrue();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void liftPublisherWhenSourceGroupedFluxThenDecorateWithGroupedFlux() {
		Flux<GroupedFlux<Integer, Integer>> source = Flux.just(1, 2, 3).groupBy(i -> i % 2);

		Function function = ReactorHooksHelper.liftPublisher(p -> true,
				(p, s) -> (CoreSubscriber) new PlusOneSubscriber(s));

		GroupedFlux<Integer, Integer> grouped = source.filter(it -> it.key() == 1).blockFirst();
		Flux<Integer> transformed = grouped.transform(function);
		assertThat(grouped).isNotSameAs(transformed);
		assertThat(transformed).isInstanceOf(SleuthGroupedLift.class).isInstanceOf(TraceContextPropagator.class);

		assertThat(((GroupedFlux) transformed).key()).isEqualTo(1);

		StepVerifier.create(transformed).expectSubscription().expectNext(2).expectNext(4).expectComplete()
				.verify(Duration.ofSeconds(5));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void sleuthMonoLiftWhenScanRunStyleThenSync() {
		final Mono source = Mockito.mock(Mono.class);

		SleuthMonoLift lifter = new SleuthMonoLift(source, (p, s) -> s);

		assertThat(lifter.scanUnsafe(Scannable.Attr.RUN_STYLE)).isEqualTo(Scannable.Attr.RunStyle.SYNC);
		assertThat(lifter.scanUnsafe(Scannable.Attr.PARENT)).isEqualTo(source);
		assertThat(lifter.scanUnsafe(Scannable.Attr.PREFETCH)).isEqualTo(Integer.MAX_VALUE);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testSleuthFluxLiftScanUnsafe() {
		final Flux source = Mockito.mock(Flux.class);

		SleuthFluxLift lifter = new SleuthFluxLift(source, (p, s) -> s);

		assertThat(lifter.scanUnsafe(Scannable.Attr.RUN_STYLE)).isEqualTo(Scannable.Attr.RunStyle.SYNC);
		assertThat(lifter.scanUnsafe(Scannable.Attr.PARENT)).isEqualTo(source);
		assertThat(lifter.scanUnsafe(Scannable.Attr.PREFETCH)).isEqualTo(lifter.getPrefetch());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testSleuthConnectableLiftScanUnsafe() {
		final ConnectableFlux source = Mockito.mock(ConnectableFlux.class);
		int sourcePrefetch = 1;
		Mockito.when(source.getPrefetch()).thenReturn(sourcePrefetch);

		SleuthConnectableLift lifter = new SleuthConnectableLift(source, (p, s) -> s);

		assertThat(lifter.scanUnsafe(Scannable.Attr.RUN_STYLE)).isEqualTo(Scannable.Attr.RunStyle.SYNC);
		assertThat(lifter.scanUnsafe(Scannable.Attr.PARENT)).isEqualTo(source);
		assertThat(lifter.scanUnsafe(Scannable.Attr.PREFETCH)).isEqualTo(sourcePrefetch);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testSleuthGroupedLiftFluxScanUnsafe() {
		final GroupedFlux source = Mockito.mock(GroupedFlux.class);
		int sourcePrefetch = 1;
		Mockito.when(source.getPrefetch()).thenReturn(sourcePrefetch);

		SleuthGroupedLift lifter = new SleuthGroupedLift(source, (p, s) -> s);

		assertThat(lifter.scanUnsafe(Scannable.Attr.RUN_STYLE)).isEqualTo(Scannable.Attr.RunStyle.SYNC);
		assertThat(lifter.scanUnsafe(Scannable.Attr.PARENT)).isEqualTo(source);
		assertThat(lifter.scanUnsafe(Scannable.Attr.PREFETCH)).isEqualTo(sourcePrefetch);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testSleuthParallelLiftScanUnsafe() {
		final ParallelFlux source = Mockito.mock(ParallelFlux.class);
		int sourcePrefetch = 1;
		Mockito.when(source.getPrefetch()).thenReturn(sourcePrefetch);

		SleuthParallelLift lifter = new SleuthParallelLift(source, (p, s) -> s);

		assertThat(lifter.scanUnsafe(Scannable.Attr.RUN_STYLE)).isEqualTo(Scannable.Attr.RunStyle.SYNC);
		assertThat(lifter.scanUnsafe(Scannable.Attr.PARENT)).isEqualTo(source);
		assertThat(lifter.scanUnsafe(Scannable.Attr.PREFETCH)).isEqualTo(sourcePrefetch);
	}

	static class CustomMonoWithoutRunStyleOperator<O> extends MonoOperator<O, O> {

		/**
		 * Build a {@link MonoOperator} wrapper around the passed parent {@link Publisher}
		 * @param source the {@link Publisher} to decorate
		 */
		protected CustomMonoWithoutRunStyleOperator(Mono<? extends O> source) {
			super(source);
		}

		@Override
		public void subscribe(CoreSubscriber<? super O> actual) {
			source.subscribe(actual);
		}

		@Nullable
		@Override
		public Object scanUnsafe(Attr key) {
			if (Attr.RUN_STYLE == key) {
				return null;
			}
			return super.scanUnsafe(key);
		}

	}

	static class PlusOneSubscriber extends BaseSubscriber<Integer> {

		CoreSubscriber<? super Integer> actual;

		PlusOneSubscriber(CoreSubscriber<? super Integer> actual) {
			this.actual = actual;
		}

		@Override
		public Context currentContext() {
			return actual.currentContext();
		}

		@Override
		protected void hookOnSubscribe(Subscription subscription) {
			actual.onSubscribe(subscription);
		}

		@Override
		protected void hookOnNext(Integer value) {
			actual.onNext(value + 1);
		}

		@Override
		protected void hookOnComplete() {
			actual.onComplete();
		}

		@Override
		protected void hookOnError(Throwable throwable) {
			actual.onError(throwable);
		}

	}

}
