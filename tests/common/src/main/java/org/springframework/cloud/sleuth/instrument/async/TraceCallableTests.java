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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;

@ExtendWith(MockitoExtension.class)
public abstract class TraceCallableTests implements TestTracingAwareSupplier {

	ExecutorService executor = Executors.newSingleThreadExecutor();

	@AfterEach
	public void clean() {
		this.executor.shutdown();
	}

	@Test
	public void should_not_see_same_trace_id_in_successive_tasks() throws Exception {
		Span firstSpan = givenCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());

		Span secondSpan = whenCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());

		BDDAssertions.then(secondSpan.context().traceId()).isNotEqualTo(firstSpan.context().traceId());
	}

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work() throws Exception {
		givenCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());

		Span secondSpan = whenNonTraceableCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());

		BDDAssertions.then(secondSpan).isNull();
	}

	@Test
	public void should_remove_parent_span_from_thread_local_after_finishing_work() throws Exception {
		Span parent = tracerTest().tracing().tracer().nextSpan().name("http:parent");
		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(parent)) {
			Span child = givenCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());
			BDDAssertions.then(parent).as("parent").isNotNull();
			BDDAssertions.then(child.context().parentId()).isEqualTo(parent.context().spanId());
		}
		BDDAssertions.then(tracerTest().tracing().tracer().currentSpan()).isNull();

		Span secondSpan = whenNonTraceableCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());

		BDDAssertions.then(secondSpan).isNull();
	}

	@Test
	public void should_take_name_of_span_from_span_name_annotation() throws Exception {
		whenATraceKeepingCallableGetsSubmitted();

		BDDAssertions.then(tracerTest().handler().reportedSpans()).hasSize(1);
		BDDAssertions.then(tracerTest().handler().reportedSpans().get(0).getName())
				.isEqualTo("some-callable-name-from-annotation");
	}

	@Test
	public void should_take_name_of_span_from_to_string_if_span_name_annotation_is_missing() throws Exception {
		whenCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());

		BDDAssertions.then(tracerTest().handler().reportedSpans()).hasSize(1);
		BDDAssertions.then(tracerTest().handler().reportedSpans().get(0).getName())
				.isEqualTo("some-callable-name-from-to-string");
	}

	private Callable<Span> thatRetrievesTraceFromThreadLocal() {
		return new Callable<Span>() {
			@Override
			public Span call() throws Exception {
				return tracerTest().tracing().tracer().currentSpan();
			}

			@Override
			public String toString() {
				return "some-callable-name-from-to-string";
			}
		};
	}

	private Span givenCallableGetsSubmitted(Callable<Span> callable)
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return whenCallableGetsSubmitted(callable);
	}

	private Span whenCallableGetsSubmitted(Callable<Span> callable)
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return this.executor
				.submit(new TraceCallable<>(tracerTest().tracing().tracer(), new DefaultSpanNamer(), callable)).get();
	}

	private Span whenATraceKeepingCallableGetsSubmitted()
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return this.executor.submit(new TraceCallable<>(tracerTest().tracing().tracer(), new DefaultSpanNamer(),
				new TraceKeepingCallable(tracerTest().tracing().tracer()))).get();
	}

	private Span whenNonTraceableCallableGetsSubmitted(Callable<Span> callable)
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return this.executor.submit(callable).get();
	}

	@SpanName("some-callable-name-from-annotation")
	static class TraceKeepingCallable implements Callable<Span> {

		public Span span;

		private final Tracer tracer;

		TraceKeepingCallable(Tracer tracer) {
			this.tracer = tracer;
		}

		@Override
		public Span call() throws Exception {
			this.span = this.tracer.currentSpan();
			return this.span;
		}

	}

}
