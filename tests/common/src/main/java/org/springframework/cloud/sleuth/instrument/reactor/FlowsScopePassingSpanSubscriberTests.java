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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.assertj.core.presentation.StandardRepresentation;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Exceptions;
import reactor.core.Scannable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth.onEachOperatorForOnEachInstrumentation;

/**
 * @author Marcin Grzejszczak
 */
public abstract class FlowsScopePassingSpanSubscriberTests {

	static final String HOOK_KEY = "org.springframework.cloud.sleuth.autoconfig.instrument.reactor.TraceReactorAutoConfiguration.TraceReactorConfiguration";

	static {
		// AssertJ will recognise QueueSubscription implements queue and try to invoke
		// iterator. That's not allowed, and will cause an exception
		// Fuseable$QueueSubscription.NOT_SUPPORTED_MESSAGE.
		// This ensures AssertJ uses normal toString.
		StandardRepresentation.registerFormatterForType(ScopePassingSpanSubscriber.class, Objects::toString);
	}

	protected abstract CurrentTraceContext currentTraceContext();

	protected abstract TraceContext context();

	AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext();

	@BeforeEach
	public void setup() {
		Hooks.resetOnEachOperator();
		Hooks.resetOnLastOperator();
		Schedulers.resetOnScheduleHooks();
	}

	@AfterEach
	public void close() {
		springContext.close();
		Hooks.resetOnEachOperator();
		Hooks.resetOnLastOperator();
		Schedulers.resetOnScheduleHooks();
	}

	@Test
	public void should_not_trace_scalar_flows() {
		springContext.registerBean(CurrentTraceContext.class, this::currentTraceContext);
		springContext.refresh();

		Function<? super Publisher<Integer>, ? extends Publisher<Integer>> transformer = onEachOperatorForOnEachInstrumentation(
				this.springContext);

		try (CurrentTraceContext.Scope ws = currentTraceContext().newScope(context())) {
			Subscriber<Object> assertNoSpanSubscriber = new CoreSubscriber<Object>() {
				@Override
				public void onSubscribe(Subscription s) {
					s.request(Long.MAX_VALUE);
					assertThat(s).isNotInstanceOf(ScopePassingSpanSubscriber.class);
				}

				@Override
				public void onNext(Object o) {

				}

				@Override
				public void onError(Throwable t) {

				}

				@Override
				public void onComplete() {

				}
			};

			Subscriber<Object> assertSpanSubscriber = new CoreSubscriber<Object>() {
				@Override
				public void onSubscribe(Subscription s) {
					s.request(Long.MAX_VALUE);
					assertThat(s).isInstanceOf(ScopePassingSpanSubscriber.class);
				}

				@Override
				public void onNext(Object o) {

				}

				@Override
				public void onError(Throwable t) {

				}

				@Override
				public void onComplete() {

				}
			};
			transformer.apply(Mono.just(1).hide()).subscribe(assertSpanSubscriber);

			transformer.apply(Mono.just(1)).subscribe(assertNoSpanSubscriber);

			transformer.apply(Mono.<Integer>error(new Exception()).hide()).subscribe(assertSpanSubscriber);

			transformer.apply(Mono.error(new Exception())).subscribe(assertNoSpanSubscriber);

			transformer.apply(Mono.<Integer>empty().hide()).subscribe(assertSpanSubscriber);

			transformer.apply(Mono.empty()).subscribe(assertNoSpanSubscriber);

		}

		Awaitility.await().untilAsserted(() -> then(currentTraceContext().context()).isNull());
	}

	@ParameterizedTest
	@MethodSource("should_not_double_wrap_async_publisher_Args")
	public void should_not_double_wrap_async_publisher(String name, Supplier<Mono<Integer>> sourceSupplier) {
		springContext.registerBean(CurrentTraceContext.class, this::currentTraceContext);
		springContext.registerBean(Tracer.class, () -> Mockito.mock(Tracer.class));
		springContext.refresh();

		Hooks.onEachOperator(HOOK_KEY, ReactorSleuth.onEachOperatorForOnEachInstrumentation(springContext));
		Hooks.onLastOperator(HOOK_KEY, ReactorSleuth.onLastOperatorForOnEachInstrumentation(springContext));

		AtomicBoolean once = new AtomicBoolean();
		Hooks.onLastOperator("test", p -> {
			// check only first onLast Hook
			if (once.compareAndSet(false, true)) {
				assertThat(p).isInstanceOf(TraceContextPropagator.class);
				Object parent = Scannable.from(p).scanUnsafe(Scannable.Attr.PARENT);
				assertThat(parent).isNotInstanceOf(TraceContextPropagator.class);
			}
			return p;
		});
		try (CurrentTraceContext.Scope ws = currentTraceContext().newScope(context())) {
			Mono<Integer> source = sourceSupplier.get();

			source.subscribe((Subscriber<? super Integer>) new CoreSubscriber<Integer>() {
				@Override
				public void onSubscribe(Subscription subscription) {
					assertThat(subscription).isInstanceOf(ScopePassingSpanSubscriber.class);
					ScopePassingSpanSubscriber<Integer> spanSubscriber = (ScopePassingSpanSubscriber) subscription;
					Object parent = spanSubscriber.scanUnsafe(Scannable.Attr.PARENT);

					assertThat(parent).isInstanceOf(Subscriber.class).isNotInstanceOf(ScopePassingSpanSubscriber.class);
				}

				@Override
				public void onNext(Integer integer) {
				}

				@Override
				public void onError(Throwable throwable) {
					throw Exceptions.propagate(throwable);
				}

				@Override
				public void onComplete() {
				}
			});

		}
	}

	private static Stream<Arguments> should_not_double_wrap_async_publisher_Args() {
		return Stream.of(
				// async source is hidden by Mono.defer()
				Arguments.of("hidden by defer",
						(Supplier) () -> Mono
								.defer(() -> Mono.fromSupplier(() -> 1).hide().subscribeOn(Schedulers.parallel()))),
				// async source is directly accessible during subscription
				Arguments.of("directly accessible",
						(Supplier) () -> Mono.fromSupplier(() -> 1).hide().subscribeOn(Schedulers.parallel())));
	}

}
