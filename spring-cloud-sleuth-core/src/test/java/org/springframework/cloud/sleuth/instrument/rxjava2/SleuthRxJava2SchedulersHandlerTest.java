package org.springframework.cloud.sleuth.instrument.rxjava2;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * @author Łukasz Guz
 */
public class SleuthRxJava2SchedulersHandlerTest {

	List<String> threadsToIgnore = new ArrayList<>();
	TraceKeys traceKeys = new TraceKeys();
	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(CurrentTraceContext.Default.create())
			.spanReporter(this.reporter).build();
	Tracer tracer = this.tracing.tracer();

	@After
	public void clean() {
		this.tracing.close();
		this.reporter.clear();
	}

	private static StringBuilder caller;

	@Before
	@After
	public void setup() {
		RxJavaPlugins.reset();
		caller = new StringBuilder();
	}

	@Test
	public void should_wrap_delegates_action_in_wrapped_action_when_delegate_is_present_on_schedule() {
		RxJavaPlugins.setScheduleHandler(runnable -> () -> {
			caller = new StringBuilder("called_from_schedulers_hook");
		});
		SleuthRxJava2SchedulersHandler sleuthRxJava2SchedulersHandler = new SleuthRxJava2SchedulersHandler(
				this.tracer, this.traceKeys, threadsToIgnore);
		Runnable action = RxJavaPlugins.onSchedule(() -> {
			caller = new StringBuilder("hello");
		});
		action.run();
		then(action).isInstanceOf(SleuthRxJava2SchedulersHandler.TraceAction.class);
		then(caller.toString()).isEqualTo("called_from_schedulers_hook");
	}

	@Test
	public void should_not_create_a_span_when_current_thread_should_be_ignored()
			throws ExecutionException, InterruptedException {
		String threadNameToIgnore = "^MyCustomThread.*$";
		RxJavaPlugins.setScheduleHandler(runnable -> {
			caller = new StringBuilder("called_from_schedulers_hook");
			return runnable;
		});
		SleuthRxJava2SchedulersHandler sleuthRxJava2SchedulersHandler = new SleuthRxJava2SchedulersHandler(
				this.tracer, this.traceKeys,
				Collections.singletonList(threadNameToIgnore));
		Future<Void> hello = executorService().submit(() -> {
			Runnable action = RxJavaPlugins
					.onSchedule(() -> caller = new StringBuilder("hello"));
			action.run();
			return null;
		});

		hello.get();

		then(this.reporter.getSpans()).isEmpty();
		then(this.tracer.currentSpan()).isNull();
	}

	private ExecutorService executorService() {
		ThreadFactory threadFactory = r -> {
			Thread thread = new Thread(r);
			thread.setName("MyCustomThread10");
			return thread;
		};
		return Executors.newSingleThreadExecutor(threadFactory);
	}

}