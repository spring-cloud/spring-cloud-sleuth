package org.springframework.cloud.sleuth.instrument;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.JdkIdGenerator;

@RunWith(MockitoJUnitRunner.class)
public class TraceCallableTest {

	ExecutorService executor = Executors.newSingleThreadExecutor();
	TraceManager traceManager = new DefaultTraceManager(new AlwaysSampler(),
			new JdkIdGenerator(), Mockito.mock(ApplicationEventPublisher.class));

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work()
			throws Exception {
		Trace firstTrace = givenCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		Trace secondTrace = whenCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		then(secondTrace.getSpan().getTraceId())
				.isNotEqualTo(firstTrace.getSpan().getTraceId());
		then(secondTrace.getSavedTrace()).isNull();
	}

	@Test
	public void should_not_find_thread_local_in_non_traceable_callback()
			throws Exception {
		givenCallableGetsSubmitted(thatRetrievesTraceFromThreadLocal());

		Trace secondTrace = whenNonTraceableCallableGetsSubmitted(
				thatRetrievesTraceFromThreadLocal());

		then(secondTrace).isNull();
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