package org.springframework.cloud.sleuth.instrument;

import static org.assertj.core.api.BDDAssertions.then;

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
public class TraceRunnableTest {

	ExecutorService executor = Executors.newSingleThreadExecutor();
	TraceManager traceManager = new DefaultTraceManager(new AlwaysSampler(),
			new JdkIdGenerator(), Mockito.mock(ApplicationEventPublisher.class));

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work() throws Exception {
		// given
		TraceSettingRunnable traceSettingRunnable = runnableThatSetsTraceInCurrentThreadLocalWithInitialTrace();
		givenRunnableGetsSubmitted(traceSettingRunnable);
		Trace firstTrace = traceSettingRunnable.trace;
		then(firstTrace).isNotNull();

		// when
		TraceKeepingRunnable traceKeepingRunnable = runnableThatRetrievesTraceFromThreadLocal();
		whenRunnableGetsSubmitted(traceKeepingRunnable);

		// then
		Trace secondTrace = traceKeepingRunnable.trace;
		then(secondTrace.getSpan().getTraceId()).isNotEqualTo(firstTrace.getSpan().getTraceId());
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
		this.executor.submit(new TraceRunnable(this.traceManager, callable)).get();
	}

	static class TraceKeepingRunnable implements Runnable {
		public Trace trace;

		@Override
		public void run() {
			this.trace = TraceContextHolder.getCurrentTrace();
		}
	}

	static class TraceSettingRunnable implements Runnable {
		public Trace trace;

		@Override
		public void run() {
			this.trace = TraceContextHolder.getCurrentTrace();
		}
	}
}