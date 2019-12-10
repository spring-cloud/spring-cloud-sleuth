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

package org.springframework.cloud.sleuth.instrument.circuitbreaker;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import brave.ScopedSpan;
import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import org.assertj.core.api.BDDAssertions;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

import static org.assertj.core.api.BDDAssertions.then;

public class CircuitBreakerTests {

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();

	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
					.addScopeDecorator(StrictScopeDecorator.create()).build())
			.spanReporter(this.reporter).sampler(Sampler.ALWAYS_SAMPLE).build();

	Tracer tracer = this.tracing.tracer();

	@Before
	public void setup() {
		this.reporter.clear();
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
			then(scopedSpan.context().traceIdString()).isEqualTo(span.context().traceIdString());
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
			BDDAssertions.thenThrownBy(
					() -> new Resilience4JCircuitBreakerFactory().create("name").run(new TraceSupplier<>(tracer, () -> {
						first.set(tracer.currentSpan());
						throw new IllegalStateException("boom");
					}), new TraceFunction<>(tracer, throwable -> {
						second.set(tracer.currentSpan());
						throw new IllegalStateException("boom2");
					}))).isInstanceOf(IllegalStateException.class).hasMessageContaining("boom2");

			then(this.reporter.getSpans()).hasSize(2);
			then(scopedSpan.context().traceIdString()).isEqualTo(first.get().context().traceIdString());
			then(scopedSpan.context().traceIdString()).isEqualTo(second.get().context().traceIdString());
			then(first.get().context().spanIdString()).isNotEqualTo(second.get().context().spanIdString());

			zipkin2.Span reportedSpan = this.reporter.getSpans().get(1);
			then(reportedSpan.tags().get("error")).contains("boom2");
		}
		finally {
			scopedSpan.finish();
		}
	}

}

class TraceSupplier<T> implements Supplier<T> {

	private final Tracer tracer;

	private final Supplier<T> delegate;

	private final AtomicReference<Span> span;

	TraceSupplier(Tracer tracer, Supplier<T> delegate) {
		this.tracer = tracer;
		this.delegate = delegate;
		this.span = new AtomicReference<>(this.tracer.nextSpan());
	}

	@Override
	public T get() {
		Span span = this.span.get();
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			return this.delegate.get();
		}
		finally {
			span.finish();
			this.span.set(null);
		}
	}

}

class TraceFunction<T> implements Function<Throwable, T> {

	private final Tracer tracer;

	private final Function<Throwable, T> delegate;

	private final AtomicReference<Span> span;

	TraceFunction(Tracer tracer, Function<Throwable, T> delegate) {
		this.tracer = tracer;
		this.delegate = delegate;
		this.span = new AtomicReference<>(this.tracer.nextSpan());
	}

	@Override
	public T apply(Throwable throwable) {
		Span span = this.span.get();
		Throwable tr = null;
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			return this.delegate.apply(throwable);
		}
		catch (Throwable t) {
			tr = t;
			throw t;
		}
		finally {
			if (tr != null) {
				String message = tr.getMessage() == null ? tr.getClass().getSimpleName() : tr.getMessage();
				span.tag("error", message);
			}
			span.finish();
			this.span.set(null);
		}
	}

}
