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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.JdkIdGenerator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(MockitoJUnitRunner.class)
public class TraceRunnableTest {

	ExecutorService executor = Executors.newSingleThreadExecutor();
	TraceManager traceManager = new DefaultTraceManager(new AlwaysSampler(),
			new JdkIdGenerator(), Mockito.mock(ApplicationEventPublisher.class));

	@After
	public void cleanup() {
		TraceContextHolder.removeCurrentTrace();
	}

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work()
			throws Exception {
		// given
		TraceKeepingRunnable traceKeepingRunnable = runnableThatRetrievesTraceFromThreadLocal();
		givenRunnableGetsSubmitted(traceKeepingRunnable);
		Trace firstTrace = traceKeepingRunnable.trace;
		then(firstTrace).as("first trace").isNotNull();

		// when
		whenRunnableGetsSubmitted(traceKeepingRunnable);

		// then
		Trace secondTrace = traceKeepingRunnable.trace;
		then(secondTrace.getSpan().getTraceId()).as("second trace id")
				.isNotEqualTo(firstTrace.getSpan().getTraceId()).as("first trace id");

		// and
		then(secondTrace.getSavedTrace()).as("saved trace as remnant of first trace")
				.isNull();
	}

	@Test
	public void should_not_find_thread_local_in_non_traceable_callback()
			throws Exception {
		// given
		TraceKeepingRunnable traceKeepingRunnable = runnableThatRetrievesTraceFromThreadLocal();
		givenRunnableGetsSubmitted(traceKeepingRunnable);
		Trace firstTrace = traceKeepingRunnable.trace;
		then(firstTrace).as("expected trace").isNotNull();

		// when
		whenNonTraceableRunnableGetsSubmitted(traceKeepingRunnable);

		// then
		Trace secondTrace = traceKeepingRunnable.trace;
		then(secondTrace).as("unexpected trace").isNull();
	}

	private TraceKeepingRunnable runnableThatRetrievesTraceFromThreadLocal() {
		return new TraceKeepingRunnable();
	}

	private void givenRunnableGetsSubmitted(Runnable runnable) throws Exception {
		whenRunnableGetsSubmitted(runnable);
	}

	private void whenRunnableGetsSubmitted(Runnable callable) throws Exception {
		this.executor.submit(new TraceRunnable(this.traceManager, callable)).get();
	}

	private void whenNonTraceableRunnableGetsSubmitted(Runnable callable)
			throws Exception {
		this.executor.submit(callable).get();
	}

	static class TraceKeepingRunnable implements Runnable {
		public Trace trace;

		@Override
		public void run() {
			this.trace = TraceContextHolder.getCurrentTrace();
		}
	}

}