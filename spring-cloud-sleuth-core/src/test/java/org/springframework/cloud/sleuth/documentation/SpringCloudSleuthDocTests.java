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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

		BDDMockito.then(tracer).should().joinTrace(BDDMockito.eq("calculateTax"), BDDMockito.any(Span.class));
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

		BDDMockito.then(tracer).should().joinTrace(BDDMockito.eq("calculateTax"), BDDMockito.any(Span.class));
	}
}
