package org.springframework.cloud.sleuth.instrument.async;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(MockitoJUnitRunner.class)
public class TraceCallableTests {

	ExecutorService executor = Executors.newSingleThreadExecutor();
	Tracer tracer = new DefaultTracer(new AlwaysSampler(),
			new Random(), Mockito.mock(ApplicationEventPublisher.class));

	@After
	public void clean() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_not_see_same_trace_id_in_successive_tasks()
			throws Exception {
		Span firstSpan = givenCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		Span secondSpan = whenCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		then(secondSpan.getTraceId())
				.isNotEqualTo(firstSpan.getTraceId());
		then(secondSpan.getSavedSpan()).isNull();
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
		Span parent = givenSpanIsAlreadyActive();
		Span child = givenCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());
		then(parent).as("parent").isNotNull();
		then(child.getSavedSpan()).isEqualTo(parent);

		Span secondSpan = whenNonTraceableCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		then(secondSpan).isNull();
	}

	private Span givenSpanIsAlreadyActive() {
		return this.tracer.startTrace(new SpanName("http", "parent"));
	}

	private Callable<Span> thatRetrievesTraceFromThreadLocal() {
		return new Callable<Span>() {
			@Override
			public Span call() throws Exception {
				return TestSpanContextHolder.getCurrentSpan();
			}
		};
	}

	private Span givenCallableGetsSubmitted(Callable<Span> callable)
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return whenCallableGetsSubmitted(callable);
	}

	private Span whenCallableGetsSubmitted(Callable<Span> callable)
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return this.executor.submit(new TraceCallable<>(this.tracer, callable))
				.get();
	}

	private Span whenNonTraceableCallableGetsSubmitted(Callable<Span> callable)
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return this.executor.submit(callable).get();
	}

}