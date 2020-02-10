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

import java.util.Objects;
import java.util.function.Function;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import org.assertj.core.presentation.StandardRepresentation;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth.scopePassingSpanOperator;

/**
 * @author Marcin Grzejszczak
 */
public class ScopePassingSpanSubscriberTests {

	static {
		// AssertJ will recognise QueueSubscription implements queue and try to invoke
		// iterator. That's not allowed, and will cause an exception
		// Fuseable$QueueSubscription.NOT_SUPPORTED_MESSAGE.
		// This ensures AssertJ uses normal toString.
		StandardRepresentation.registerFormatterForType(ScopePassingSpanSubscriber.class, Objects::toString);
	}

	final CurrentTraceContext currentTraceContext = CurrentTraceContext.Default.create();

	TraceContext context = TraceContext.newBuilder().traceId(1).spanId(1).sampled(true).build();

	TraceContext context2 = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();

	AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext();

	@After
	public void close() {
		springContext.close();
	}

	@Test
	public void should_propagate_current_context() {
		ScopePassingSpanSubscriber<?> subscriber = new ScopePassingSpanSubscriber<>(null, Context.of("foo", "bar"),
				this.currentTraceContext, null);

		then((String) subscriber.currentContext().get("foo")).isEqualTo("bar");
	}

	@Test
	public void should_set_empty_context_when_context_is_null() {
		ScopePassingSpanSubscriber<?> subscriber = new ScopePassingSpanSubscriber<>(null, Context.empty(),
				this.currentTraceContext, null);

		then(subscriber.currentContext().isEmpty()).isTrue();
	}

	@Test
	public void should_put_current_span_to_context() {
		try (Scope ws = this.currentTraceContext.newScope(context2)) {
			CoreSubscriber<?> subscriber = new ScopePassingSpanSubscriber<>(new BaseSubscriber<Object>() {
			}, Context.empty(), currentTraceContext, context);

			then(subscriber.currentContext().get(TraceContext.class)).isEqualTo(context);
		}
	}

	@Test
	public void should_not_trace_scalar_flows() {
		springContext.registerBean(CurrentTraceContext.class, () -> currentTraceContext);
		springContext.refresh();

		Awaitility.await().untilAsserted(() -> {
			Function<? super Publisher<Integer>, ? extends Publisher<Integer>> transformer = scopePassingSpanOperator(
					this.springContext);

			try (Scope ws = this.currentTraceContext.newScope(context)) {
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

			then(this.currentTraceContext.get()).isNull();
		});
	}

}
