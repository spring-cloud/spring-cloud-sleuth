package org.springframework.cloud.sleuth.instrument.async;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.TraceRunnable;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(MockitoJUnitRunner.class)
public class TraceRunnableTests {

	ExecutorService executor = Executors.newSingleThreadExecutor();
	Tracer tracer = new DefaultTracer(new AlwaysSampler(),
			new Random(), new DefaultSpanNamer(),
			new NoOpSpanLogger(), new NoOpSpanReporter(), new TraceKeys());

	@After
	public void cleanup() {
		TestSpanContextHolder.removeCurrentSpan();
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
		then(secondSpan.getTraceId()).as("second span id")
				.isNotEqualTo(firstSpan.getTraceId()).as("first span id");

		// and
		then(secondSpan.getSavedSpan()).as("saved span as remnant of first span")
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

		then(traceKeepingRunnable.span).hasNameEqualTo("some-runnable-name-from-annotation");
	}

	@Test
	public void should_take_name_of_span_from_to_string_if_span_name_annotation_is_missing()
			throws Exception {
		final AtomicReference<Span> span = new AtomicReference<>();
		Runnable runnable = runnableWithCustomToString(span);

		whenRunnableGetsSubmitted(runnable);

		then(span.get()).hasNameEqualTo("some-runnable-name-from-to-string");
	}

	private TraceKeepingRunnable runnableThatRetrievesTraceFromThreadLocal() {
		return new TraceKeepingRunnable();
	}

	private void givenRunnableGetsSubmitted(Runnable runnable) throws Exception {
		whenRunnableGetsSubmitted(runnable);
	}

	private void whenRunnableGetsSubmitted(Runnable runnable) throws Exception {
		this.executor.submit(new TraceRunnable(this.tracer, new DefaultSpanNamer(), runnable)).get();
	}

	private void whenNonTraceableRunnableGetsSubmitted(Runnable runnable)
			throws Exception {
		this.executor.submit(runnable).get();
	}

	private Runnable runnableWithCustomToString(final AtomicReference<Span> span) {
		return new Runnable() {
			@Override
			public void run() {
				span.set(TestSpanContextHolder.getCurrentSpan());
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
			this.span = TestSpanContextHolder.getCurrentSpan();
		}
	}

}