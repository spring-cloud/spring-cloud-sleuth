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
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.core.publisher.Operators;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.util.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.cloud.sleuth.instrument.reactor.ReactorHooksHelper.named;

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
		Mono<?> syncSourceWithLifter = Mono.delay(Duration.ofMillis(10)).as(TraceContextPropagatorOperator::new);
		boolean actual = ReactorHooksHelper.shouldDecorate(syncSourceWithLifter.map(Function.identity()));
		assertThat(actual).isFalse();
	}

	@Test
	public void shouldDecorateWhenAsyncSourceAndScopePassingLifterThanShouldNotDecorate() {
		Function<? super Publisher<Long>, ? extends Publisher<Long>> traceContextPropagator = Operators.liftPublisher(
				publisher -> true,
				named(ReactorHooksHelper.LIFTER_NAME, (publisher, coreSubscriber) -> coreSubscriber));
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

	static class CustomMonoWithoutRunStyleOperator<O> extends MonoOperator<O, O> {

		/**
		 * Build a {@link MonoOperator} wrapper around the passed parent {@link Publisher}
		 * @param source the {@link Publisher} to decorate
		 */
		CustomMonoWithoutRunStyleOperator(Mono<? extends O> source) {
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

	static class TraceContextPropagatorOperator<O> extends MonoOperator<O, O> implements TraceContextPropagator {

		/**
		 * Build a {@link MonoOperator} wrapper around the passed parent {@link Publisher}
		 * @param source the {@link Publisher} to decorate
		 */
		TraceContextPropagatorOperator(Mono<? extends O> source) {
			super(source);
		}

		@Override
		public void subscribe(CoreSubscriber<? super O> actual) {
			source.subscribe(actual);
		}

		@Nullable
		@Override
		public Object scanUnsafe(Attr key) {
			// just to check that it pass by instanceof
			if (Attr.RUN_STYLE == key) {
				return null;
			}
			return super.scanUnsafe(key);
		}

	}

}
