/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(MockitoJUnitRunner.class)
public class TraceCallableTests {

	ExecutorService executor = Executors.newSingleThreadExecutor();
	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(new StrictCurrentTraceContext())
			.spanReporter(this.reporter)
			.build();
	Tracer tracer = this.tracing.tracer();

	@After
	public void clean() {
		this.tracing.close();
		this.reporter.clear();
		this.executor.shutdown();
	}

	@Test
	public void should_not_see_same_trace_id_in_successive_tasks()
			throws Exception {
		Span firstSpan = givenCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		Span secondSpan = whenCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		then(secondSpan.context().traceId())
				.isNotEqualTo(firstSpan.context().traceId());
	}

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work()
			throws Exception {
		givenCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());

		Span secondSpan = whenNonTraceableCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		then(secondSpan).isNull();
	}

	@Test
	public void should_remove_parent_span_from_thread_local_after_finishing_work()
			throws Exception {
		Span parent = this.tracer.nextSpan().name("http:parent");
		try(Tracer.SpanInScope ws = this.tracer.withSpanInScope(parent)){
			Span child = givenCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());
			then(parent).as("parent").isNotNull();
			then(child.context().parentId()).isEqualTo(parent.context().spanId());
		}
		then(this.tracer.currentSpan()).isNull();

		Span secondSpan = whenNonTraceableCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		then(secondSpan).isNull();
	}

	@Test
	public void should_take_name_of_span_from_span_name_annotation()
			throws Exception {
		whenATraceKeepingCallableGetsSubmitted();

		then(this.reporter.getSpans()).hasSize(1);
		then(this.reporter.getSpans().get(0).name()).isEqualTo("some-callable-name-from-annotation");
	}

	@Test
	public void should_take_name_of_span_from_to_string_if_span_name_annotation_is_missing()
			throws Exception {
		whenCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		then(this.reporter.getSpans()).hasSize(1);
		then(this.reporter.getSpans().get(0).name()).isEqualTo("some-callable-name-from-to-string");
	}

	private Callable<Span> thatRetrievesTraceFromThreadLocal() {
		return new Callable<Span>() {
			@Override
			public Span call() throws Exception {
				return tracer.currentSpan();
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
		return this.executor.submit(new TraceCallable<>(this.tracing, new DefaultSpanNamer(),
				callable)).get();
	}
	private Span whenATraceKeepingCallableGetsSubmitted()
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return this.executor.submit(new TraceCallable<>(this.tracing, new DefaultSpanNamer(),
				new TraceKeepingCallable())).get();
	}

	private Span whenNonTraceableCallableGetsSubmitted(Callable<Span> callable)
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return this.executor.submit(callable).get();
	}

	@SpanName("some-callable-name-from-annotation")
	static class TraceKeepingCallable implements Callable<Span> {
		public Span span;

		@Override
		public Span call() throws Exception {
			this.span = Tracing.currentTracer().currentSpan();
			return this.span;
		}
	}

}