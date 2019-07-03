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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import brave.Span;
import brave.Tracer;
import brave.sampler.Sampler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.awaitility.Awaitility;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpanSubscriberTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class SpanSubscriberTests {

	private static final Log log = LogFactory.getLog(SpanSubscriberTests.class);

	@Autowired
	Tracer tracer;

	@Autowired
	ConfigurableApplicationContext factory;

	@Test
	public void should_pass_tracing_info_when_using_reactor() {
		Span span = this.tracer.nextSpan().name("foo").start();
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();
		Publisher<Integer> traced = Flux.just(1, 2, 3);
		log.info("Hello");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			Flux.from(traced).map(d -> d + 1).map(d -> d + 1).map((d) -> {
				spanInOperation.set(this.tracer.currentSpan());
				return d + 1;
			}).map(d -> d + 1).subscribe(System.out::println);
		}
		finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
		then(spanInOperation.get().context().spanId()).isEqualTo(span.context().spanId());
	}

	@Test
	public void should_support_reactor_fusion_optimization() {
		Span span = this.tracer.nextSpan().name("foo").start();
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();
		log.info("Hello");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			Mono.just(1).flatMap(d -> Flux.just(d + 1).collectList().map(p -> p.get(0)))
					.map(d -> d + 1).map((d) -> {
						spanInOperation.set(this.tracer.currentSpan());
						return d + 1;
					}).map(d -> d + 1).subscribe(System.out::println);
		}
		finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
		then(spanInOperation.get().context().spanId()).isEqualTo(span.context().spanId());
	}

	@Test
	public void should_not_trace_scalar_flows() {
		Span span = this.tracer.nextSpan().name("foo").start();
		log.info("Hello");

		// Disable global hooks for local hook testing
		TraceReactorAutoConfigurationAccessorConfiguration.close();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {

			Function<? super Publisher<Integer>, ? extends Publisher<Integer>> transformer = ReactorSleuth
					.scopePassingSpanOperator(this.factory);

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

			transformer.apply(Mono.<Integer>error(new Exception()).hide())
					.subscribe(assertSpanSubscriber);

			transformer.apply(Mono.error(new Exception()))
					.subscribe(assertNoSpanSubscriber);

			transformer.apply(Mono.<Integer>empty().hide())
					.subscribe(assertSpanSubscriber);

			transformer.apply(Mono.empty()).subscribe(assertNoSpanSubscriber);

		}
		finally {
			span.finish();
		}

		Awaitility.await().untilAsserted(() -> {
			then(this.tracer.currentSpan()).isNull();
		});

		TraceReactorAutoConfigurationAccessorConfiguration.setup(this.factory);
	}

	@Test
	public void should_pass_tracing_info_when_using_reactor_async() {
		Span span = this.tracer.nextSpan().name("foo").start();
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();
		log.info("Hello");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			Flux.just(1, 2, 3).publishOn(Schedulers.single()).log("reactor.1")
					.map(d -> d + 1).map(d -> d + 1)
					.publishOn(Schedulers.newSingle("secondThread")).log("reactor.2")
					.map((d) -> {
						spanInOperation.set(this.tracer.currentSpan());
						return d + 1;
					}).map(d -> d + 1).blockLast();

			Awaitility.await().untilAsserted(() -> {
				then(spanInOperation.get().context().traceId())
						.isEqualTo(span.context().traceId());
			});
			then(this.tracer.currentSpan()).isEqualTo(span);
		}
		finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
		Span foo2 = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(foo2)) {
			Flux.just(1, 2, 3).publishOn(Schedulers.single()).log("reactor.")
					.map(d -> d + 1).map(d -> d + 1).map((d) -> {
						spanInOperation.set(this.tracer.currentSpan());
						return d + 1;
					}).map(d -> d + 1).blockLast();

			then(this.tracer.currentSpan()).isEqualTo(foo2);
			// parent cause there's an async span in the meantime
			then(spanInOperation.get().context().traceId())
					.isEqualTo(foo2.context().traceId());
		}
		finally {
			foo2.finish();
		}

		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void checkSequenceOfOperations() {
		Span parentSpan = this.tracer.nextSpan().name("foo").start();
		log.info("Hello");
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(parentSpan)) {
			final Long spanId = Mono.fromCallable(this.tracer::currentSpan)
					.map(span -> span.context().spanId()).block();
			then(spanId).isNotNull();

			final Long secondSpanId = Mono.fromCallable(this.tracer::currentSpan)
					.map(span -> span.context().spanId()).block();
			then(secondSpanId).isEqualTo(spanId); // different trace ids here
		}
	}

	@Test
	public void checkTraceIdDuringZipOperation() {
		Span initSpan = this.tracer.nextSpan().name("foo").start();
		final AtomicReference<Long> spanInOperation = new AtomicReference<>();
		final AtomicReference<Long> spanInZipOperation = new AtomicReference<>();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(initSpan)) {
			Mono.fromCallable(this.tracer::currentSpan)
					.map(span -> span.context().spanId()).doOnNext(spanInOperation::set)
					.zipWith(Mono.fromCallable(this.tracer::currentSpan)
							.map(span -> span.context().spanId())
							.doOnNext(spanInZipOperation::set))
					.block();
		}

		then(spanInZipOperation).hasValue(initSpan.context().spanId()); // ok here
		then(spanInOperation).hasValue(initSpan.context().spanId()); // Expecting
		// <AtomicReference[null]>
		// to have value:
		// <1L> but did
		// not.
	}

	// #646
	@Test
	public void should_work_for_mono_just_with_flat_map() {
		Span initSpan = this.tracer.nextSpan().name("foo").start();
		log.info("Hello");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(initSpan)) {
			Mono.just("value1")
					.flatMap(request -> Mono.just("value2").then(Mono.just("foo")))
					.map(a -> "qwe").block();
		}
	}

	// #1030
	@Test
	public void checkTraceIdFromSubscriberContext() {
		Span initSpan = this.tracer.nextSpan().name("foo").start();
		final AtomicReference<Long> spanInSubscriberContext = new AtomicReference<>();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(initSpan)) {
			Mono.subscriberContext()
					.map(context -> this.tracer.currentSpan().context().spanId())
					.doOnNext(spanInSubscriberContext::set).block();
		}

		then(spanInSubscriberContext).hasValue(initSpan.context().spanId()); // ok here
	}

	@Test
	public void should_pass_tracing_info_into_inner_publishers() {
		Span span = this.tracer.nextSpan().name("foo").start();
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			Flux
					.range(0, 5)
					.flatMap(it -> Mono
							.delay(Duration.ofMillis(1))
							.map(context -> this.tracer.currentSpan())
							.doOnNext(spanInOperation::set)
					)
					.blockFirst();
		}
		finally {
			span.finish();
		}

		then(spanInOperation.get().context().spanId()).isEqualTo(span.context().spanId());
	}

	@EnableAutoConfiguration
	@Configuration
	static class Config {

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

}
