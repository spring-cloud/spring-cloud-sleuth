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

package org.springframework.cloud.sleuth.instrument.circuitbreaker;

import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.sleuth.ScopedSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;

public abstract class ReactiveCircuitBreakerTests implements TestTracingAwareSupplier {

	@Test
	public void should_pass_tracing_information_when_using_circuit_breaker() {
		// given
		Tracer tracer = tracerTest().tracing().tracer();
		ScopedSpan scopedSpan = null;
		try {
			scopedSpan = tracer.startScopedSpan("start");
			// when
			Span span = new TraceReactiveCircuitBreaker(new ReactiveResilience4JCircuitBreakerFactory().create("name"),
					tracer, tracerTest().tracing().currentTraceContext())
							.run(Mono.defer(() -> Mono.just(tracer.currentSpan()))).block();

			BDDAssertions.then(span).isNotNull();
			BDDAssertions.then(scopedSpan.context().traceId()).isEqualTo(span.context().traceId());
		}
		finally {
			scopedSpan.end();
		}
	}

	@Test
	public void should_pass_tracing_information_when_using_circuit_breaker_with_fallback() {
		// given
		Tracer tracer = tracerTest().tracing().tracer();
		AtomicReference<Span> first = new AtomicReference<>();
		AtomicReference<Span> second = new AtomicReference<>();
		ScopedSpan scopedSpan = null;
		try {
			scopedSpan = tracer.startScopedSpan("start");
			// when
			BDDAssertions.thenThrownBy(() -> new TraceReactiveCircuitBreaker(
					new ReactiveResilience4JCircuitBreakerFactory().create("name"), tracer,
					tracerTest().tracing().currentTraceContext()).run(Mono.defer(() -> {
						first.set(tracer.currentSpan());
						throw new IllegalStateException("boom");
					}), throwable -> {
						second.set(tracer.currentSpan());
						throw new IllegalStateException("boom2");
					}).block()).isInstanceOf(IllegalStateException.class).hasMessageContaining("boom2");

			BDDAssertions.then(tracerTest().handler().reportedSpans()).hasSize(2);
			BDDAssertions.then(first.get()).isNotNull();
			BDDAssertions.then(second.get()).isNotNull();
			BDDAssertions.then(scopedSpan.context().traceId()).isEqualTo(first.get().context().traceId());
			BDDAssertions.then(scopedSpan.context().traceId()).isEqualTo(second.get().context().traceId());
			BDDAssertions.then(first.get().context().spanId()).isNotEqualTo(second.get().context().spanId());

			FinishedSpan finishedSpan = tracerTest().handler().reportedSpans().get(1);
			BDDAssertions.then(finishedSpan.getName()).contains("function");
			additionalAssertions(finishedSpan);
		}
		finally {
			scopedSpan.end();
		}
	}

	public void additionalAssertions(FinishedSpan finishedSpan) {
		throw new UnsupportedOperationException("Assert errors");
	}

}
