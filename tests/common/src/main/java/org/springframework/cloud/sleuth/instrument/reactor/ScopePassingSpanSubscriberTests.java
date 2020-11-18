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
import java.util.function.Function;

import org.assertj.core.presentation.StandardRepresentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth.scopePassingSpanOperator;
import static org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY;
import static org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfiguration.TraceReactorConfiguration.SLEUTH_TRACE_REACTOR_KEY;

/**
 * @author Marcin Grzejszczak
 */
public abstract class ScopePassingSpanSubscriberTests {

	static {
		// AssertJ will recognise QueueSubscription implements queue and try to invoke
		// iterator. That's not allowed, and will cause an exception
		// Fuseable$QueueSubscription.NOT_SUPPORTED_MESSAGE.
		// This ensures AssertJ uses normal toString.
		StandardRepresentation.registerFormatterForType(ScopePassingSpanSubscriber.class, Objects::toString);
	}

	protected abstract CurrentTraceContext currentTraceContext();

	protected abstract TraceContext context();

	protected abstract TraceContext context2();

	Subscriber<Object> assertNotScopePassingSpanSubscriber = new CoreSubscriber<Object>() {
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

	Subscriber<Object> assertScopePassingSpanSubscriber = new CoreSubscriber<Object>() {
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

	AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext();

	@BeforeEach
	public void resetHooks() {
		// There's an assumption some other test is leaking hooks, so we clear them all to
		// prevent should_not_scope_scalar_subscribe from being interfered with.
		Hooks.resetOnEachOperator(SLEUTH_TRACE_REACTOR_KEY);
		Hooks.resetOnLastOperator(SLEUTH_TRACE_REACTOR_KEY);
		Schedulers.removeExecutorServiceDecorator(SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY);
	}

	@AfterEach
	public void close() {
		springContext.close();
	}

	@Test
	public void should_propagate_current_context() {
		ScopePassingSpanSubscriber<?> subscriber = new ScopePassingSpanSubscriber<>(null, Context.of("foo", "bar"),
				currentTraceContext(), null);

		then((String) subscriber.currentContext().get("foo")).isEqualTo("bar");
	}

	/**
	 * This ensures when the desired context is in the reactor context we don't copy it.
	 */
	@Test
	public void should_not_redundantly_copy_context() {
		Context initial = Context.of(TraceContext.class, context());
		ScopePassingSpanSubscriber<?> subscriber = new ScopePassingSpanSubscriber<>(null, initial,
				currentTraceContext(), context());

		then(initial.get(TraceContext.class)).isSameAs(subscriber.currentContext().get(TraceContext.class));
	}

	@Test
	public void should_set_empty_context_when_context_is_null() {
		ScopePassingSpanSubscriber<?> subscriber = new ScopePassingSpanSubscriber<>(null, Context.empty(),
				currentTraceContext(), null);

		then(subscriber.currentContext().isEmpty()).isTrue();
	}

	@Test
	public void should_put_current_span_to_context() {
		try (CurrentTraceContext.Scope ws = currentTraceContext().newScope(context2())) {
			CoreSubscriber<?> subscriber = new ScopePassingSpanSubscriber<>(new BaseSubscriber<Object>() {
			}, Context.empty(), currentTraceContext(), context());

			then(subscriber.currentContext().get(TraceContext.class)).isEqualTo(context());
		}
	}

	@Test
	public void should_not_scope_scalar_subscribe() {
		springContext.registerBean(CurrentTraceContext.class, this::currentTraceContext);
		springContext.refresh();

		Function<? super Publisher<Integer>, ? extends Publisher<Integer>> transformer = scopePassingSpanOperator(
				this.springContext);

		try (CurrentTraceContext.Scope ws = currentTraceContext().newScope(context())) {

			transformer.apply(Mono.just(1)).subscribe(assertNotScopePassingSpanSubscriber);

			transformer.apply(Mono.error(new Exception())).subscribe(assertNotScopePassingSpanSubscriber);

			transformer.apply(Mono.empty()).subscribe(assertNotScopePassingSpanSubscriber);

		}
	}

	@Test
	public void should_scope_scalar_hide_subscribe() {
		springContext.registerBean(CurrentTraceContext.class, this::currentTraceContext);
		springContext.refresh();

		Function<? super Publisher<Integer>, ? extends Publisher<Integer>> transformer = scopePassingSpanOperator(
				this.springContext);

		try (CurrentTraceContext.Scope ws = currentTraceContext().newScope(context())) {

			transformer.apply(Mono.just(1).hide()).subscribe(assertScopePassingSpanSubscriber);

			transformer.apply(Mono.<Integer>error(new Exception()).hide()).subscribe(assertScopePassingSpanSubscriber);

			transformer.apply(Mono.<Integer>empty().hide()).subscribe(assertScopePassingSpanSubscriber);
		}
	}

}
