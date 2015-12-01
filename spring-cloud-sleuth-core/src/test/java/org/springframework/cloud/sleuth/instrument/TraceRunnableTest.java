package org.springframework.cloud.sleuth.instrument;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;

@RunWith(MockitoJUnitRunner.class)
public class TraceRunnableTest {

	ExecutorService executor = Executors.newSingleThreadExecutor();
	TraceManager traceManager = Mockito.mock(TraceManager.class);

	@Test
	@Ignore("Will fail because trace is not removed after runnable gets executed")
	public void should_remove_span_from_thread_local_after_finishing_work() throws Exception {
		// given
		TraceSettingRunnable traceSettingRunnable = runnableThatSetsTraceInCurrentThreadLocalWithInitialTrace();
		givenRunnableGetsSubmitted(traceSettingRunnable);
		then(traceSettingRunnable.trace).isNotNull();

		// when
		TraceKeepingRunnable traceKeepingRunnable = runnableThatRetrievesTraceFromThreadLocal();
		whenRunnableGetsSubmitted(traceKeepingRunnable);

		// then
		then(traceKeepingRunnable.trace).isNull();
	}

	private TraceSettingRunnable runnableThatSetsTraceInCurrentThreadLocalWithInitialTrace() {
		return new TraceSettingRunnable();
	}

	private TraceKeepingRunnable runnableThatRetrievesTraceFromThreadLocal() {
		return new TraceKeepingRunnable();
	}

	private void givenRunnableGetsSubmitted(Runnable runnable) throws Exception {
		whenRunnableGetsSubmitted(runnable);
	}

	private void whenRunnableGetsSubmitted(Runnable callable) throws Exception {
		executor.submit(new TraceRunnable(traceManager, callable)).get();
	}

	static class TraceKeepingRunnable implements Runnable {
		public Trace trace;

		@Override
		public void run() {
			trace = TraceContextHolder.getCurrentTrace();
		}
	}

	static class TraceSettingRunnable implements Runnable {
		public Trace trace;

		@Override
		public void run() {
			TraceContextHolder.setCurrentTrace(Mockito.mock(Trace.class));
			trace = TraceContextHolder.getCurrentTrace();
		}
	}
}