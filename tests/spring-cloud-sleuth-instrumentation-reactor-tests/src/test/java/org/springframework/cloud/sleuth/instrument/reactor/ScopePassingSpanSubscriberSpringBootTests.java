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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Like {@link ScopePassingSpanSubscriberTests}, except this tests wiring with spring boot
 * config.
 */
@SpringBootTest(classes = ScopePassingSpanSubscriberSpringBootTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ScopePassingSpanSubscriberSpringBootTests {

	@Autowired
	CurrentTraceContext currentTraceContext;

	TraceContext context = TraceContext.newBuilder().traceId(1).spanId(1).sampled(true)
			.build();

	TraceContext context2 = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true)
			.build();

	@Test
	public void should_pass_tracing_info_when_using_reactor() {
		final AtomicReference<TraceContext> spanInOperation = new AtomicReference<>();
		Publisher<Integer> traced = Flux.just(1, 2, 3);

		try (Scope ws = this.currentTraceContext.newScope(context)) {
			Flux.from(traced).map(d -> d + 1).map(d -> d + 1).map((d) -> {
				spanInOperation.set(this.currentTraceContext.get());
				return d + 1;
			}).map(d -> d + 1).subscribe(d -> {
			});
		}

		then(this.currentTraceContext.get()).isNull();
		then(spanInOperation.get()).isEqualTo(context);
	}

	@Test
	public void should_support_reactor_fusion_optimization() {
		final AtomicReference<TraceContext> spanInOperation = new AtomicReference<>();

		try (Scope ws = this.currentTraceContext.newScope(context)) {
			Mono.just(1).flatMap(d -> Flux.just(d + 1).collectList().map(p -> p.get(0)))
					.map(d -> d + 1).map((d) -> {
						spanInOperation.set(this.currentTraceContext.get());
						return d + 1;
					}).map(d -> d + 1).subscribe(d -> {
					});
		}

		then(this.currentTraceContext.get()).isNull();
		then(spanInOperation.get()).isEqualTo(context);
	}

	@Test
	public void should_pass_tracing_info_when_using_reactor_async() {
		final AtomicReference<TraceContext> spanInOperation = new AtomicReference<>();

		try (Scope ws = this.currentTraceContext.newScope(context)) {
			Flux.just(1, 2, 3).publishOn(Schedulers.single()).log("reactor.1")
					.map(d -> d + 1).map(d -> d + 1)
					.publishOn(Schedulers.newSingle("secondThread")).log("reactor.2")
					.map((d) -> {
						spanInOperation.set(this.currentTraceContext.get());
						return d + 1;
					}).map(d -> d + 1).blockLast();

			Awaitility.await()
					.untilAsserted(() -> then(spanInOperation.get()).isEqualTo(context));
			then(this.currentTraceContext.get()).isEqualTo(context);
		}

		then(this.currentTraceContext.get()).isNull();

		try (Scope ws = this.currentTraceContext.newScope(context2)) {
			Flux.just(1, 2, 3).publishOn(Schedulers.single()).log("reactor.")
					.map(d -> d + 1).map(d -> d + 1).map((d) -> {
						spanInOperation.set(this.currentTraceContext.get());
						return d + 1;
					}).map(d -> d + 1).blockLast();

			then(this.currentTraceContext.get()).isEqualTo(context2);
			then(spanInOperation.get()).isEqualTo(context2);
		}

		then(this.currentTraceContext.get()).isNull();
	}

	@Test
	public void should_pass_tracing_info_when_using_reactor_async_processor() {
		final AtomicReference<TraceContext> spanInOperation = new AtomicReference<>();

		Sinks.One<Integer> one = Sinks.one();
		try (Scope ws = this.currentTraceContext.newScope(context)) {
			one.asMono()
					// hook is not applied for Processors so first operator after it will
					// not be decorated with brave context
					.map(d -> d + 1).map((d) -> {
						spanInOperation.set(this.currentTraceContext.get());
						return d + 1;
					}).map(d -> d + 1).doOnSubscribe(subscription -> {
						final Thread thread = new Thread(() -> {
							try {
								Thread.sleep(300);
							}
							catch (InterruptedException e) {
								e.printStackTrace();
							}
							one.emitValue(0);
						});
						thread.setName("async_processor_source");
						thread.setDaemon(true);
						thread.start();
					}).subscribeOn(Schedulers.boundedElastic()).block();

			Awaitility.await()
					.untilAsserted(() -> then(spanInOperation.get()).isEqualTo(context));
			then(this.currentTraceContext.get()).isEqualTo(context);
		}

		then(this.currentTraceContext.get()).isNull();
	}

	@Test
	public void onlyConsidersContextDuringSubscribe() {
		Mono<TraceContext> fromMono = Mono.fromCallable(this.currentTraceContext::get);

		try (Scope ws = this.currentTraceContext.newScope(context)) {
			then(fromMono.map(context -> context).block()).isNotNull();
		}
	}

	@Test
	public void checkTraceIdDuringZipOperation() {
		final AtomicReference<TraceContext> spanInOperation = new AtomicReference<>();
		final AtomicReference<TraceContext> spanInZipOperation = new AtomicReference<>();

		try (Scope ws = this.currentTraceContext.newScope(context)) {
			Mono.fromCallable(this.currentTraceContext::get).map(span -> span)
					.doOnNext(spanInOperation::set)
					.zipWith(Mono.fromCallable(this.currentTraceContext::get)
							.map(span -> span).doOnNext(spanInZipOperation::set))
					.block();
		}

		then(spanInZipOperation).hasValue(context);
		then(spanInOperation).hasValue(context);
	}

	// #646
	@Test
	public void should_work_for_mono_just_with_flat_map() {
		try (Scope ws = this.currentTraceContext.newScope(context)) {
			Mono.just("value1")
					.flatMap(request -> Mono.just("value2").then(Mono.just("foo")))
					.map(a -> "qwe").block();
		}
	}

	// #1030
	@Test
	public void checkTraceIdFromSubscriberContext() {
		final AtomicReference<TraceContext> spanInSubscriberContext = new AtomicReference<>();

		try (Scope ws = this.currentTraceContext.newScope(context)) {
			Mono.subscriberContext().map(context -> this.currentTraceContext.get())
					.doOnNext(spanInSubscriberContext::set).block();
		}

		then(spanInSubscriberContext).hasValue(context); // ok here
	}

	@Test
	public void should_pass_tracing_info_into_inner_publishers() {
		final AtomicReference<TraceContext> spanInOperation = new AtomicReference<>();

		try (Scope ws = this.currentTraceContext.newScope(context)) {
			Flux.range(0, 5)
					.flatMap(it -> Mono.delay(Duration.ofMillis(1))
							.map(context -> this.currentTraceContext.get())
							.doOnNext(spanInOperation::set))
					.blockFirst();
		}

		then(spanInOperation.get()).isEqualTo(context);
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
