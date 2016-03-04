/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.documentation;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.TraceRunnable;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * Test class to be embedded in the
 * {@code docs/src/main/asciidoc/spring-cloud-sleuth.adoc} file
 *
 * @author Marcin Grzejszczak
 */
public class SpringCloudSleuthDocTests {


	@Configuration
	public class SamplingConfiguration {
		// tag::always_sampler[]
		@Bean
		public Sampler defaultSampler() {
			return new AlwaysSampler();
		}
		// end::always_sampler[]
	}

	// tag::span_name_annotation[]
	@SpanName("calculateTax")
	class TaxCountingRunnable implements Runnable {

		@Override public void run() {
			// perform logic
		}
	}
	// end::span_name_annotation[]

	@Test
	public void should_set_runnable_name_to_annotated_value()
			throws ExecutionException, InterruptedException {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		SpanNamer spanNamer = new DefaultSpanNamer();
		Tracer tracer = Mockito.mock(Tracer.class);

		// tag::span_name_annotated_runnable_execution[]
		Runnable runnable = new TraceRunnable(tracer, spanNamer, new TaxCountingRunnable());
		Future<?> future = executorService.submit(runnable);
		// ... some additional logic ...
		future.get();
		// end::span_name_annotated_runnable_execution[]

		BDDMockito.then(tracer).should().createSpan(BDDMockito.eq("calculateTax"), BDDMockito.any(Span.class));
	}

	@Test
	public void should_set_runnable_name_to_to_string_value()
			throws ExecutionException, InterruptedException {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		SpanNamer spanNamer = new DefaultSpanNamer();
		Tracer tracer = Mockito.mock(Tracer.class);

		// tag::span_name_to_string_runnable_execution[]
		Runnable runnable = new TraceRunnable(tracer, spanNamer, new Runnable() {
			@Override public void run() {
				// perform logic
			}

			@Override public String toString() {
				return "calculateTax";
			}
		});
		Future<?> future = executorService.submit(runnable);
		// ... some additional logic ...
		future.get();
		// end::span_name_to_string_runnable_execution[]

		BDDMockito.then(tracer).should().createSpan(BDDMockito.eq("calculateTax"), BDDMockito.any(Span.class));
		executorService.shutdown();
	}

	ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
	Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(), this.publisher, new DefaultSpanNamer());

	@Test
	public void should_create_a_span_with_tracer() {
		String taxValue = "10";

		// tag::manual_span_creation[]
		// Start a span. If there was a span present in this thread it will become
		// the `newSpan`'s parent.
		Span newSpan = this.tracer.createSpan("calculateTax");
		try {
			// ...
			// You can tag a span
			this.tracer.addTag("taxValue", taxValue);
			// ...
			// You can log an event on a span
			newSpan.logEvent("taxCalculated");
		} finally {
			// Once done remember to close the span. This will allow collecting
			// the span to send it to Zipkin
			this.tracer.close(newSpan);
		}
		// end::manual_span_creation[]

		then(this.tracer.getCurrentSpan()).isNull();
		then(newSpan).isNotNull();
	}

	@Test
	public void should_continue_a_span_with_tracer() throws Exception {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		String taxValue = "10";
		Span initialSpan = this.tracer.createSpan("calculateTax");
		assertThat(initialSpan.tags()).doesNotContainKeys("taxValue");
		assertThat(initialSpan.logs()).extracting("event").doesNotContain("taxCalculated");

		executorService.submit(() -> {
			// tag::manual_span_continuation[]
			// let's assume that we're in a thread Y and we've received
			// the `initialSpan` from thread X
			Span continuedSpan = this.tracer.continueSpan(initialSpan);
			try {
				// ...
				// You can tag a span
				this.tracer.addTag("taxValue", taxValue);
				// ...
				// You can log an event on a span
				continuedSpan.logEvent("taxCalculated");
			} finally {
				// Once done remember to detach the span. That way you'll
				// safely remove it from the current thread without closing it
				this.tracer.detach(continuedSpan);
			}
			// end::manual_span_continuation[]
		}
		).get();

		this.tracer.close(initialSpan);
		then(this.tracer.getCurrentSpan()).isNull();
		then(initialSpan)
				.hasATag("taxValue", taxValue)
				.hasLoggedAnEvent("taxCalculated");
		executorService.shutdown();
	}

	@Test
	public void should_join_a_span_with_tracer() throws Exception {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		String commissionValue = "10";
		Span initialSpan = this.tracer.createSpan("calculateTax");
		assertThat(initialSpan.tags()).doesNotContainKeys("commissionValue");
		assertThat(initialSpan.logs()).extracting("event").doesNotContain("commissionCalculated");

		executorService.submit(() -> {
			// tag::manual_span_joining[]
			// let's assume that we're in a thread Y and we've received
			// the `initialSpan` from thread X. `initialSpan` will be the parent
			// of the `joinedSpan`
			Span joinedSpan = this.tracer.createSpan("calculateCommission", initialSpan);
			try {
				// ...
				// You can tag a span
				this.tracer.addTag("commissionValue", commissionValue);
				// ...
				// You can log an event on a span
				joinedSpan.logEvent("commissionCalculated");
			} finally {
				// Once done remember to close the span. This will allow collecting
				// the span to send it to Zipkin. The tags and events set on the
				// joinedSpan will not be present on the parent
				this.tracer.close(joinedSpan);
			}
			// end::manual_span_joining[]
		}
		).get();

		this.tracer.close(initialSpan);
		then(this.tracer.getCurrentSpan()).isNull();
		assertThat(initialSpan.tags()).doesNotContainKeys("commissionValue");
		assertThat(initialSpan.logs()).extracting("event").doesNotContain("commissionCalculated");
		executorService.shutdown();
	}
}
