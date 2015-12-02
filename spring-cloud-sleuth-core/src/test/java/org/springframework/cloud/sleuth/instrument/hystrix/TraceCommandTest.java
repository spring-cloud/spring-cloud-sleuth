package org.springframework.cloud.sleuth.instrument.hystrix;

import static com.netflix.hystrix.HystrixCommand.Setter.withGroupKey;
import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey;
import static org.assertj.core.api.BDDAssertions.then;

import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.JdkIdGenerator;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;

public class TraceCommandTest {

	static final String EXPECTED_TRACE_ID = "A";
	TraceManager traceManager = new DefaultTraceManager(new AlwaysSampler(),
			new JdkIdGenerator(), Mockito.mock(ApplicationEventPublisher.class));

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work()
			throws Exception {
		Trace firstTraceFromHystrix = givenACommandWasExecuted(traceReturningCommand());

		Trace secondTraceFromHystrix = whenCommandIsExecuted(traceReturningCommand());

		then(secondTraceFromHystrix.getSpan().getTraceId()).as("second trace id")
				.isNotEqualTo(firstTraceFromHystrix.getSpan().getTraceId()).as("first trace id");
		then(secondTraceFromHystrix.getSavedTrace()).as("saved trace as remnant of first trace")
				.isNull();

		cleanupTrace(firstTraceFromHystrix);
		cleanupTrace(secondTraceFromHystrix);
	}

	@Test
	public void should_run_Hystrix_command_with_span_passed_from_parent_thread() {
		Trace trace = givenATraceIsPresentInTheCurrentThread();
		TraceCommand<Trace> command = traceReturningCommand();

		Trace traceFromCommand = whenCommandIsExecuted(command);

		then(traceFromCommand).as("Trace from the Hystrix Thread").isNotNull();
		then(traceFromCommand.getSpan().getTraceId()).isEqualTo(EXPECTED_TRACE_ID);

		cleanupTrace(trace);
	}

	private void cleanupTrace(Trace trace) {
		traceManager.close(trace);
	}

	@After
	public void cleanUpTrace() {
		TraceContextHolder.removeCurrentTrace();
	}

	private Trace givenATraceIsPresentInTheCurrentThread() {
		return traceManager.startSpan("test", MilliSpan.builder().traceId(EXPECTED_TRACE_ID).build());
	}

	private TraceCommand<Trace> traceReturningCommand() {
		return new TraceCommand<Trace>(traceManager,  withGroupKey(asKey(""))
				.andCommandKey(HystrixCommandKey.Factory.asKey("")).andThreadPoolPropertiesDefaults(
						HystrixThreadPoolProperties.Setter().withMaxQueueSize(1).withCoreSize(1))) {
			@Override
			public Trace doRun() throws Exception {
				return TraceContextHolder.getCurrentTrace();
			}
		};
	}

	private Trace whenCommandIsExecuted(TraceCommand<Trace> command) {
		return command.execute();
	}

	private Trace givenACommandWasExecuted(TraceCommand<Trace> command) {
		return whenCommandIsExecuted(command);
	}
}