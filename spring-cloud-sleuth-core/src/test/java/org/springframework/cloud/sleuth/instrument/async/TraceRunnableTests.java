package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.ExceptionMessageErrorParser;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(MockitoJUnitRunner.class)
public class TraceRunnableTests {

	ExecutorService executor = Executors.newSingleThreadExecutor();
	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(CurrentTraceContext.Default.create())
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
		return new TraceKeepingRunnable();
	}

	private void givenRunnableGetsSubmitted(Runnable runnable) throws Exception {
		whenRunnableGetsSubmitted(runnable);
	}

	private void whenRunnableGetsSubmitted(Runnable runnable) throws Exception {
		this.executor.submit(new TraceRunnable(this.tracing.tracer(), new DefaultSpanNamer(),
				new ExceptionMessageErrorParser(), runnable)).get();
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
		public Span span;

		@Override
		public void run() {
			this.span = Tracing.currentTracer().currentSpan();
		}
	}

}