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

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Like {@link ScopePassingSpanSubscriberTests}, except this tests wiring with spring boot
 * config.
 */
@ContextConfiguration(classes = ScopePassingSpanSubscriberSpringBootTests.TestConfig.class)
public abstract class ScopePassingSpanSubscriberSpringBootTests {

	@Autowired
	CurrentTraceContext currentTraceContext;

	protected abstract TraceContext context();

	protected abstract TraceContext context2();

	@Test
	public void should_pass_tracing_info_when_using_reactor() {
		final AtomicReference<TraceContext> spanInOperation = new AtomicReference<>();
		Publisher<Integer> traced = Flux.just(1, 2, 3);

		try (CurrentTraceContext.Scope ws = this.currentTraceContext.newScope(context())) {
			Flux.from(traced).map(d -> d + 1).map(d -> d + 1).map((d) -> {
				spanInOperation.set(this.currentTraceContext.context());
				return d + 1;
			}).map(d -> d + 1).subscribe(d -> {
			});
		}

		then(this.currentTraceContext.context()).isNull();
		then(spanInOperation.get()).isEqualTo(context());
	}

	@Test
	public void should_support_reactor_fusion_optimization() {
		final AtomicReference<TraceContext> spanInOperation = new AtomicReference<>();

		try (CurrentTraceContext.Scope ws = this.currentTraceContext.newScope(context())) {
			Mono.just(1).flatMap(d -> Flux.just(d + 1).collectList().map(p -> p.get(0))).map(d -> d + 1).map((d) -> {
				spanInOperation.set(this.currentTraceContext.context());
				return d + 1;
			}).map(d -> d + 1).subscribe(d -> {
			});
		}

		then(this.currentTraceContext.context()).isNull();
		then(spanInOperation.get()).isEqualTo(context());
	}

	@Test
	public void should_pass_tracing_info_when_using_reactor_async() {
		final AtomicReference<TraceContext> spanInOperation = new AtomicReference<>();

		try (CurrentTraceContext.Scope ws = this.currentTraceContext.newScope(context())) {
			Flux.just(1, 2, 3).publishOn(Schedulers.single()).log("reactor.1").map(d -> d + 1).map(d -> d + 1)
					.publishOn(Schedulers.newSingle("secondThread")).log("reactor.2").map((d) -> {
						spanInOperation.set(this.currentTraceContext.context());
						return d + 1;
					}).map(d -> d + 1).blockLast();

			Awaitility.await().untilAsserted(() -> then(spanInOperation.get()).isEqualTo(context()));
			then(this.currentTraceContext.context()).isEqualTo(context());
		}

		then(this.currentTraceContext.context()).isNull();

		try (CurrentTraceContext.Scope ws = this.currentTraceContext.newScope(context2())) {
			Flux.just(1, 2, 3).publishOn(Schedulers.single()).log("reactor.").map(d -> d + 1).map(d -> d + 1)
					.map((d) -> {
						spanInOperation.set(this.currentTraceContext.context());
						return d + 1;
					}).map(d -> d + 1).blockLast();

			then(this.currentTraceContext.context()).isEqualTo(context2());
			then(spanInOperation.get()).isEqualTo(context2());
		}

		then(this.currentTraceContext.context()).isNull();
	}

	@Test
	public void onlyConsidersContextDuringSubscribe() {
		Mono<TraceContext> fromMono = Mono.fromCallable(this.currentTraceContext::context);

		try (CurrentTraceContext.Scope ws = this.currentTraceContext.newScope(context())) {
			then(fromMono.map(context -> context).block()).isNotNull();
		}
	}

	@Test
	public void checkTraceIdDuringZipOperation() {
		final AtomicReference<TraceContext> spanInOperation = new AtomicReference<>();
		final AtomicReference<TraceContext> spanInZipOperation = new AtomicReference<>();

		try (CurrentTraceContext.Scope ws = this.currentTraceContext.newScope(context())) {
			Mono.fromCallable(this.currentTraceContext::context).map(span -> span).doOnNext(spanInOperation::set)
					.zipWith(Mono.fromCallable(this.currentTraceContext::context).map(span -> span)
							.doOnNext(spanInZipOperation::set))
					.block();
		}

		then(spanInZipOperation).hasValue(context());
		then(spanInOperation).hasValue(context());
	}

	// #646
	@Test
	public void should_work_for_mono_just_with_flat_map() {
		try (CurrentTraceContext.Scope ws = this.currentTraceContext.newScope(context())) {
			Mono.just("value1").flatMap(request -> Mono.just("value2").then(Mono.just("foo"))).map(a -> "qwe").block();
		}
	}

	// #1030
	@Test
	public void checkTraceIdFromSubscriberContext() {
		final AtomicReference<TraceContext> spanInSubscriberContext = new AtomicReference<>();

		try (CurrentTraceContext.Scope ws = this.currentTraceContext.newScope(context())) {
			Mono.subscriberContext().map(context -> this.currentTraceContext.context())
					.doOnNext(spanInSubscriberContext::set).block();
		}

		then(spanInSubscriberContext).hasValue(context()); // ok here
	}

	@Test
	public void should_pass_tracing_info_into_inner_publishers() {
		final AtomicReference<TraceContext> spanInOperation = new AtomicReference<>();

		try (CurrentTraceContext.Scope ws = this.currentTraceContext.newScope(context())) {
			Flux.range(0, 5).flatMap(it -> Mono.delay(Duration.ofMillis(1))
					.map(context -> this.currentTraceContext.context()).doOnNext(spanInOperation::set)).blockFirst();
		}

		then(spanInOperation.get()).isEqualTo(context());
	}

	@EnableAutoConfiguration
	@Configuration(proxyBeanMethods = false)
	static class TestConfig {

	}

}
