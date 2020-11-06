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

package org.springframework.cloud.sleuth.documentation;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.brave.bridge.BraveBaggageManager;
import org.springframework.cloud.sleuth.brave.bridge.BraveTracer;
import org.springframework.cloud.sleuth.instrument.async.TraceCallable;
import org.springframework.cloud.sleuth.instrument.async.TraceRunnable;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Test class to be embedded in the
 * {@code docs/src/main/asciidoc/spring-cloud-sleuth.adoc} file. They use Sleuth's API
 * with Brave as tracer implementation.
 *
 * @author Marcin Grzejszczak
 */
public class SpringCloudSleuthDocTests {

	TestSpanHandler spans = new TestSpanHandler();

	StrictCurrentTraceContext braveCurrentTraceContext = StrictCurrentTraceContext.create();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(this.braveCurrentTraceContext)
			.sampler(Sampler.ALWAYS_SAMPLE).addSpanHandler(this.spans).build();

	brave.Tracer braveTracer = this.tracing.tracer();

	BraveBaggageManager braveBaggageManager = new BraveBaggageManager();

	org.springframework.cloud.sleuth.api.Tracer tracer = BraveTracer.fromBrave(this.braveTracer,
			this.braveBaggageManager);

	@BeforeEach
	public void setup() {
		this.spans.clear();
	}

	@AfterEach
	public void close() {
		this.tracing.close();
		this.braveCurrentTraceContext.close();
	}

	@Test
	public void should_set_runnable_name_to_annotated_value() throws ExecutionException, InterruptedException {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		SpanNamer spanNamer = new DefaultSpanNamer();

		// tag::span_name_annotated_runnable_execution[]
		Runnable runnable = new TraceRunnable(this.tracer, spanNamer, new TaxCountingRunnable());
		Future<?> future = executorService.submit(runnable);
		// ... some additional logic ...
		future.get();
		// end::span_name_annotated_runnable_execution[]

		then(this.spans).hasSize(1);
		then(this.spans.get(0).name()).isEqualTo("calculateTax");
	}

	@Test
	public void should_set_runnable_name_to_to_string_value() throws ExecutionException, InterruptedException {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		SpanNamer spanNamer = new DefaultSpanNamer();

		// tag::span_name_to_string_runnable_execution[]
		Runnable runnable = new TraceRunnable(this.tracer, spanNamer, new Runnable() {
			@Override
			public void run() {
				// perform logic
			}

			@Override
			public String toString() {
				return "calculateTax";
			}
		});
		Future<?> future = executorService.submit(runnable);
		// ... some additional logic ...
		future.get();
		// end::span_name_to_string_runnable_execution[]

		then(this.spans).hasSize(1);
		then(this.spans.get(0).name()).isEqualTo("calculateTax");
		executorService.shutdown();
	}

	@Test
	public void should_create_a_span_with_tracer() {
		String taxValue = "10";

		// tag::manual_span_creation[]
		// Start a span. If there was a span present in this thread it will become
		// the `newSpan`'s parent.
		Span newSpan = this.tracer.nextSpan().name("calculateTax");
		try (Tracer.SpanInScope ws = this.tracer.withSpan(newSpan.start())) {
			// ...
			// You can tag a span
			newSpan.tag("taxValue", taxValue);
			// ...
			// You can log an event on a span
			newSpan.event("taxCalculated");
		}
		finally {
			// Once done remember to end the span. This will allow collecting
			// the span to send it to a distributed tracing system e.g. Zipkin
			newSpan.end();
		}
		// end::manual_span_creation[]

		then(this.spans).hasSize(1);
		then(this.spans.get(0).name()).isEqualTo("calculateTax");
		then(this.spans.get(0).tags()).containsEntry("taxValue", "10");
		then(this.spans.get(0).annotations()).hasSize(1);
	}

	@Test
	public void should_continue_a_span_with_tracer() throws Exception {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		String taxValue = "10";
		// tag::manual_span_continuation[]
		Span spanFromThreadX = this.tracer.nextSpan().name("calculateTax");
		try (Tracer.SpanInScope ws = this.tracer.withSpan(spanFromThreadX.start())) {
			executorService.submit(() -> {
				// Pass the span from thread X
				Span continuedSpan = spanFromThreadX;
				// ...
				// You can tag a span
				continuedSpan.tag("taxValue", taxValue);
				// ...
				// You can log an event on a span
				continuedSpan.event("taxCalculated");
			}).get();
		}
		finally {
			spanFromThreadX.end();
		}
		// end::manual_span_continuation[]

		BDDAssertions.then(spans).hasSize(1);
		BDDAssertions.then(spans.get(0).name()).isEqualTo("calculateTax");
		BDDAssertions.then(spans.get(0).tags()).containsEntry("taxValue", "10");
		BDDAssertions.then(spans.get(0).annotations()).hasSize(1);
		executorService.shutdown();
	}

	@Test
	public void should_start_a_span_with_explicit_parent() throws Exception {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		String commissionValue = "10";
		Span initialSpan = this.tracer.nextSpan().name("calculateTax").start();

		executorService.submit(() -> {
			// tag::manual_span_joining[]
			// let's assume that we're in a thread Y and we've received
			// the `initialSpan` from thread X. `initialSpan` will be the parent
			// of the `newSpan`
			Span newSpan = null;
			try (Tracer.SpanInScope ws = this.tracer.withSpan(initialSpan)) {
				newSpan = this.tracer.nextSpan().name("calculateCommission");
				// ...
				// You can tag a span
				newSpan.tag("commissionValue", commissionValue);
				// ...
				// You can log an event on a span
				newSpan.event("commissionCalculated");
			}
			finally {
				// Once done remember to end the span. This will allow collecting
				// the span to send it to e.g. Zipkin. The tags and events set on the
				// newSpan will not be present on the parent
				if (newSpan != null) {
					newSpan.end();
				}
			}
			// end::manual_span_joining[]
		}).get();

		Optional<MutableSpan> calculateTax = spans.spans().stream()
				.filter(span -> span.name().equals("calculateCommission")).findFirst();
		BDDAssertions.then(calculateTax).isPresent();
		BDDAssertions.then(calculateTax.get().tags()).containsEntry("commissionValue", "10");
		BDDAssertions.then(calculateTax.get().annotations()).hasSize(1);
		executorService.shutdown();
	}

	@Test
	public void should_wrap_runnable_in_its_sleuth_representative()
			throws InterruptedException, ExecutionException, TimeoutException {
		SpanNamer spanNamer = new DefaultSpanNamer();
		// tag::trace_runnable[]
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				// do some work
			}

			@Override
			public String toString() {
				return "spanNameFromToStringMethod";
			}
		};
		// Manual `TraceRunnable` creation with explicit "calculateTax" Span name
		Runnable traceRunnable = new TraceRunnable(this.tracer, spanNamer, runnable, "calculateTax");
		// end::trace_runnable[]

		ExecutorService executorService = Executors.newSingleThreadExecutor();
		executorService.submit(traceRunnable).get(10, TimeUnit.MILLISECONDS);
		Optional<MutableSpan> calculateTax = spans.spans().stream().filter(span -> span.name().equals("calculateTax"))
				.findFirst();
		BDDAssertions.then(calculateTax).isPresent();
		executorService.shutdown();
	}

	@Test
	public void should_wrap_callable_in_its_sleuth_representative()
			throws InterruptedException, ExecutionException, TimeoutException {
		SpanNamer spanNamer = new DefaultSpanNamer();
		// tag::trace_callable[]
		Callable<String> callable = new Callable<String>() {
			@Override
			public String call() throws Exception {
				return someLogic();
			}

			@Override
			public String toString() {
				return "spanNameFromToStringMethod";
			}
		};
		// Manual `TraceCallable` creation with explicit "calculateTax" Span name
		Callable<String> traceCallable = new TraceCallable<>(tracer, spanNamer, callable, "calculateTax");
		// end::trace_callable[]

		ExecutorService executorService = Executors.newSingleThreadExecutor();
		String result = executorService.submit(traceCallable).get(10, TimeUnit.MILLISECONDS);
		BDDAssertions.then(result).isEqualTo("some logic");
		Optional<MutableSpan> calculateTax = spans.spans().stream().filter(span -> span.name().equals("calculateTax"))
				.findFirst();
		BDDAssertions.then(calculateTax).isPresent();
		executorService.shutdown();
	}

	private String someLogic() {
		return "some logic";
	}

	@Configuration(proxyBeanMethods = false)
	static class SamplingConfiguration {

		// tag::always_sampler[]
		@Bean
		public Sampler defaultSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}
		// end::always_sampler[]

	}

	// tag::span_name_annotation[]
	@SpanName("calculateTax")
	class TaxCountingRunnable implements Runnable {

		@Override
		public void run() {
			// perform logic
		}

	}
	// end::span_name_annotation[]

}
