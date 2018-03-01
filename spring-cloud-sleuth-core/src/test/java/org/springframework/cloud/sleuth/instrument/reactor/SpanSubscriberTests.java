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

import java.util.concurrent.atomic.AtomicReference;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.sampler.Sampler;
import org.junit.BeforeClass;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpanSubscriberTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class SpanSubscriberTests {

	private static final Log log = LogFactory.getLog(SpanSubscriberTests.class);

	@Autowired Tracer tracer;

	@Test public void should_pass_tracing_info_when_using_reactor() {
		Span span = this.tracer.nextSpan().name("foo").start();
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();
		Publisher<Integer> traced = Flux.just(1, 2, 3);
		log.info("Hello");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			Flux.from(traced)
					.map( d -> d + 1)
					.map( d -> d + 1)
					.map( (d) -> {
						spanInOperation.set(
								SpanSubscriberTests.this.tracer.currentSpan());
						return d + 1;
					})
					.map( d -> d + 1)
					.subscribe(System.out::println);
		} finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
		then(spanInOperation.get().context().traceId())
				.isEqualTo(span.context().traceId());
	}

	@Test public void should_support_reactor_fusion_optimization() {
		Span span = this.tracer.nextSpan().name("foo").start();
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();
		log.info("Hello");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			Mono.just(1).flatMap(d -> Flux.just(d + 1).collectList().map(p -> p.get(0)))
					.map(d -> d + 1).map((d) -> {
				spanInOperation.set(SpanSubscriberTests.this.tracer.currentSpan());
				return d + 1;
			}).map(d -> d + 1).subscribe(System.out::println);
		} finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
		then(spanInOperation.get().context().traceId()).isEqualTo(span.context().traceId());
	}

	@Test public void should_not_trace_scalar_flows() {
		Span span = this.tracer.nextSpan().name("foo").start();
		final AtomicReference<Subscription> spanInOperation = new AtomicReference<>();
		log.info("Hello");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			Mono.just(1).subscribe(new BaseSubscriber<Integer>() {
				@Override protected void hookOnSubscribe(Subscription subscription) {
					spanInOperation.set(subscription);
				}
			});

			then(this.tracer.currentSpan()).isNotNull();
			then(spanInOperation.get()).isNotInstanceOf(SpanSubscriber.class);

			Mono.<Integer>error(new Exception())
					.subscribe(new BaseSubscriber<Integer>() {
						@Override
						protected void hookOnSubscribe(Subscription subscription) {
							spanInOperation.set(subscription);
						}

						@Override
						protected void hookOnError(Throwable throwable) {
						}
					});

			then(this.tracer.currentSpan()).isNotNull();
			then(spanInOperation.get()).isNotInstanceOf(SpanSubscriber.class);

			Mono.<Integer>empty()
					.subscribe(new BaseSubscriber<Integer>() {
						@Override
						protected void hookOnSubscribe(Subscription subscription) {
							spanInOperation.set(subscription);
						}
					});

			then(this.tracer.currentSpan()).isNotNull();
			then(spanInOperation.get()).isNotInstanceOf(SpanSubscriber.class);
		} finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void should_pass_tracing_info_when_using_reactor_async() {
		Span span = this.tracer.nextSpan().name("foo").start();
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();
		log.info("Hello");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			Flux.just(1, 2, 3).publishOn(Schedulers.single()).log("reactor.1")
					.map(d -> d + 1).map(d -> d + 1).publishOn(Schedulers.newSingle("secondThread")).log("reactor.2")
					.map((d) -> {
						spanInOperation.set(SpanSubscriberTests.this.tracer.currentSpan());
						return d + 1;
					}).map(d -> d + 1).blockLast();

			Awaitility.await().untilAsserted(() -> {
				then(spanInOperation.get().context().traceId()).isEqualTo(span.context().traceId());
			});
			then(this.tracer.currentSpan()).isEqualTo(span);
		} finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
		Span foo2 = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(foo2)) {
			Flux.just(1, 2, 3).publishOn(Schedulers.single()).log("reactor.").map(d -> d + 1).map(d -> d + 1).map((d) -> {
				spanInOperation.set(SpanSubscriberTests.this.tracer.currentSpan());
				return d + 1;
			}).map(d -> d + 1).blockLast();

			then(this.tracer.currentSpan()).isEqualTo(foo2);
			// parent cause there's an async span in the meantime
			then(spanInOperation.get().context().traceId()).isEqualTo(foo2.context().traceId());
		} finally {
			foo2.finish();
		}

		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void checkSequenceOfOperations() {
		Span parentSpan = this.tracer.nextSpan().name("foo").start();
		log.info("Hello");
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(parentSpan)) {
			final Long traceId = Mono.fromCallable(tracer::currentSpan)
					.map(span -> span.context().traceId())
					.block();
			then(traceId).isNotNull();

			final Long secondTraceId = Mono.fromCallable(tracer::currentSpan)
					.map(span -> span.context().traceId())
					.block();
			then(secondTraceId).isEqualTo(traceId); // different trace ids here
		}
	}

	@Test
	public void checkTraceIdDuringZipOperation() {
		Span initSpan = this.tracer.nextSpan().name("foo").start();
		final AtomicReference<Long> spanInOperation = new AtomicReference<>();
		final AtomicReference<Long> spanInZipOperation = new AtomicReference<>();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(initSpan)) {
			Mono.fromCallable(tracer::currentSpan)
					.map(span -> span.context().traceId())
					.doOnNext(spanInOperation::set)
					.zipWith(
							Mono.fromCallable(tracer::currentSpan)
									.map(span -> span.context().traceId())
									.doOnNext(spanInZipOperation::set))
					.block();
		}

		then(spanInZipOperation).hasValue(initSpan.context().traceId()); // ok here
		then(spanInOperation).hasValue(initSpan.context().traceId()); // Expecting <AtomicReference[null]> to have value: <1L> but did not.
	}

	// #646
	@Test
	public void should_work_for_mono_just_with_flat_map() {
		Span initSpan = this.tracer.nextSpan().name("foo").start();
		log.info("Hello");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(initSpan)) {
			Mono.just("value1").flatMap(request -> Mono.just("value2").then(Mono.just("foo")))
					.map(a -> "qwe").block();
		}
	}

	@AfterClass
	public static void cleanup() {
		Hooks.resetOnLastOperator();
		Hooks.resetOnEachOperator();
		Schedulers.resetFactory();
	}

	@EnableAutoConfiguration
	@Configuration
	static class Config {
		@Bean Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}
	}
}