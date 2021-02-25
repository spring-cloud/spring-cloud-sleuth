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

import brave.ScopedSpan;
import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.assertj.core.api.BDDAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;

import static org.assertj.core.api.BDDAssertions.then;

public class CircuitBreakerTests {

	StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();

	TestSpanHandler spans = new TestSpanHandler();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(this.currentTraceContext)
			.addSpanHandler(this.spans).sampler(Sampler.ALWAYS_SAMPLE).build();

	Tracer tracer = this.tracing.tracer();

	@Before
	public void setup() {
		this.spans.clear();
	}

	@After
	public void close() {
		this.tracing.close();
		this.currentTraceContext.close();
	}

	@Test
	public void should_pass_tracing_information_when_using_circuit_breaker() {
		// given
		Tracer tracer = this.tracer;
		ScopedSpan scopedSpan = null;
		try {
			scopedSpan = tracer.startScopedSpan("start");
			// when
			Span span = new Resilience4JCircuitBreakerFactory().create("name")
					.run(new TraceSupplier<>(tracer, tracer::currentSpan));

			then(span).isNotNull();
			then(scopedSpan.context().traceIdString())
					.isEqualTo(span.context().traceIdString());
		}
		finally {
			scopedSpan.finish();
		}
	}

	@Test
	public void should_pass_tracing_information_when_using_circuit_breaker_with_fallback() {
		// given
		Tracer tracer = this.tracer;
		AtomicReference<Span> first = new AtomicReference<>();
		AtomicReference<Span> second = new AtomicReference<>();
		ScopedSpan scopedSpan = null;
		try {
			scopedSpan = tracer.startScopedSpan("start");
			// when
			BDDAssertions.thenThrownBy(() -> new Resilience4JCircuitBreakerFactory()
					.create("name").run(new TraceSupplier<>(tracer, () -> {
						first.set(tracer.currentSpan());
						throw new IllegalStateException("boom");
					}), new TraceFunction<>(tracer, throwable -> {
						second.set(tracer.currentSpan());
						throw new IllegalStateException("boom2");
					}))).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("boom2");

			then(this.spans).hasSize(2);
			then(scopedSpan.context().traceIdString())
					.isEqualTo(first.get().context().traceIdString());
			then(scopedSpan.context().traceIdString())
					.isEqualTo(second.get().context().traceIdString());
			then(first.get().context().spanIdString())
					.isNotEqualTo(second.get().context().spanIdString());

			MutableSpan reportedSpan = this.spans.get(1);
			then(reportedSpan.name()).contains("CircuitBreakerTests");
			then(reportedSpan.tags().get("error")).contains("boom2");
		}
		finally {
			scopedSpan.finish();
		}
	}

}
