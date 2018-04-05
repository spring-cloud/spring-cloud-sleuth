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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

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
public class TraceRunnableTests {

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
	public void should_remove_span_from_thread_local_after_finishing_work()
			throws Exception {
		// given
		TraceKeepingRunnable traceKeepingRunnable = runnableThatRetrievesTraceFromThreadLocal();
		givenRunnableGetsSubmitted(traceKeepingRunnable);
		Span firstSpan = traceKeepingRunnable.span;
		then(firstSpan).as("first span").isNotNull();

		// when
		whenRunnableGetsSubmitted(traceKeepingRunnable);

		// then
		Span secondSpan = traceKeepingRunnable.span;
		then(secondSpan.context().traceId()).as("second span id")
				.isNotEqualTo(firstSpan.context().traceId()).as("first span id");

		// and
		then(secondSpan.context().parentId()).as("saved span as remnant of first span")
				.isNull();
	}

	@Test
	public void should_not_find_thread_local_in_non_traceable_callback()
			throws Exception {
		// given
		TraceKeepingRunnable traceKeepingRunnable = runnableThatRetrievesTraceFromThreadLocal();
		givenRunnableGetsSubmitted(traceKeepingRunnable);
		Span firstSpan = traceKeepingRunnable.span;
		then(firstSpan).as("expected span").isNotNull();

		// when
		whenNonTraceableRunnableGetsSubmitted(traceKeepingRunnable);

		// then
		Span secondSpan = traceKeepingRunnable.span;
		then(secondSpan).as("unexpected span").isNull();
	}

	@Test
	public void should_take_name_of_span_from_span_name_annotation()
			throws Exception {
		TraceKeepingRunnable traceKeepingRunnable = runnableThatRetrievesTraceFromThreadLocal();

		whenRunnableGetsSubmitted(traceKeepingRunnable);

		then(this.reporter.getSpans()).hasSize(1);
		then(this.reporter.getSpans().get(0).name()).isEqualTo("some-runnable-name-from-annotation");
	}

	@Test
	public void should_take_name_of_span_from_to_string_if_span_name_annotation_is_missing()
			throws Exception {
		final AtomicReference<Span> span = new AtomicReference<>();
		Runnable runnable = runnableWithCustomToString(span);

		whenRunnableGetsSubmitted(runnable);

		then(this.reporter.getSpans()).hasSize(1);
		then(this.reporter.getSpans().get(0).name()).isEqualTo("some-runnable-name-from-to-string");
	}

	private TraceKeepingRunnable runnableThatRetrievesTraceFromThreadLocal() {
		return new TraceKeepingRunnable(this.tracer);
	}

	private void givenRunnableGetsSubmitted(Runnable runnable) throws Exception {
		whenRunnableGetsSubmitted(runnable);
	}

	private void whenRunnableGetsSubmitted(Runnable runnable) throws Exception {
		this.executor.submit(new TraceRunnable(this.tracing, new DefaultSpanNamer(),
				runnable)).get();
	}

	private void whenNonTraceableRunnableGetsSubmitted(Runnable runnable)
			throws Exception {
		this.executor.submit(runnable).get();
	}

	private Runnable runnableWithCustomToString(final AtomicReference<Span> span) {
		return new Runnable() {
			@Override
			public void run() {
				span.set(tracer.currentSpan());
			}

			@Override public String toString() {
				return "some-runnable-name-from-to-string";
			}
		};
	}

	@SpanName("some-runnable-name-from-annotation")
	static class TraceKeepingRunnable implements Runnable {

		private final Tracer tracer;

		public Span span;

		TraceKeepingRunnable(Tracer tracer) {
			this.tracer = tracer;
		}

		@Override
		public void run() {
			this.span = this.tracer.currentSpan();
		}
	}

}