package org.springframework.cloud.sleuth.instrument;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.cloud.sleuth.util.RandomLongSpanIdGenerator;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(MockitoJUnitRunner.class)
public class TraceCallableTests {

	ExecutorService executor = Executors.newSingleThreadExecutor();
	TraceManager traceManager = new DefaultTraceManager(new AlwaysSampler(),
			new RandomLongSpanIdGenerator(), Mockito.mock(ApplicationEventPublisher.class));

	@After
	public void clean() {
		TraceContextHolder.removeCurrentTrace();
	}

	@Test
	public void should_not_see_same_trace_id_in_successive_tasks()
			throws Exception {
		Trace firstTrace = givenCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		Trace secondTrace = whenCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		then(secondTrace.getSpan().getTraceId())
				.isNotEqualTo(firstTrace.getSpan().getTraceId());
		then(secondTrace.getSaved()).isNull();
	}

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work()
			throws Exception {
		givenCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());

		Trace secondTrace = whenNonTraceableCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		then(secondTrace).isNull();
	}

	@Test
	public void should_remove_parent_span_from_thread_local_after_finishing_work()
			throws Exception {
		Trace parent = givenSpanIsAlreadyActive();
		Trace child = givenCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());
		then(parent).as("parent").isNotNull();
		then(child.getSaved()).isEqualTo(parent);

		Trace secondTrace = whenNonTraceableCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		then(secondTrace).isNull();
	}

	private Trace givenSpanIsAlreadyActive() {
		return this.traceManager.startSpan("parent");
	}

	private Callable<Trace> thatRetrievesTraceFromThreadLocal() {
		return new Callable<Trace>() {
			@Override
			public Trace call() throws Exception {
				return TraceContextHolder.getCurrentTrace();
			}
		};
	}

	private Trace givenCallableGetsSubmitted(Callable<Trace> callable)
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return whenCallableGetsSubmitted(callable);
	}

	private Trace whenCallableGetsSubmitted(Callable<Trace> callable)
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return this.executor.submit(new TraceCallable<>(this.traceManager, callable))
				.get();
	}

	private Trace whenNonTraceableCallableGetsSubmitted(Callable<Trace> callable)
			throws InterruptedException, java.util.concurrent.ExecutionException {
		return this.executor.submit(callable).get();
	}

}