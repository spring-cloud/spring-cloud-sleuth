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

package org.springframework.cloud.sleuth.instrument.circuitbreaker;

import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.sleuth.api.ScopedSpan;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;

public abstract class CircuitBreakerTests implements TestTracingAwareSupplier {

	@Test
	public void should_pass_tracing_information_when_using_circuit_breaker() {
		// given
		Tracer tracer = tracerTest().tracing().tracer();
		ScopedSpan scopedSpan = null;
		try {
			scopedSpan = tracer.startScopedSpan("start");
			// when
			Span span = new Resilience4JCircuitBreakerFactory().create("name")
					.run(new TraceSupplier<>(tracerTest().tracing().tracer(), tracer::currentSpan));

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
			BDDAssertions.thenThrownBy(() -> new Resilience4JCircuitBreakerFactory().create("name")
					.run(new TraceSupplier<>(tracerTest().tracing().tracer(), () -> {
						first.set(tracer.currentSpan());
						throw new IllegalStateException("boom");
					}), new TraceFunction<>(tracerTest().tracing().tracer(), throwable -> {
						second.set(tracer.currentSpan());
						throw new IllegalStateException("boom2");
					}))).isInstanceOf(IllegalStateException.class).hasMessageContaining("boom2");

			BDDAssertions.then(tracerTest().handler().reportedSpans()).hasSize(2);
			BDDAssertions.then(first.get()).isNotNull();
			BDDAssertions.then(second.get()).isNotNull();
			BDDAssertions.then(scopedSpan.context().traceId()).isEqualTo(first.get().context().traceId());
			BDDAssertions.then(scopedSpan.context().traceId()).isEqualTo(second.get().context().traceId());
			BDDAssertions.then(first.get().context().spanId()).isNotEqualTo(second.get().context().spanId());

			FinishedSpan finishedSpan = tracerTest().handler().reportedSpans().get(1);
			BDDAssertions.then(finishedSpan.getName()).contains("CircuitBreakerTests");
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
