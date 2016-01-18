package org.springframework.cloud.sleuth.instrument.hystrix;

import static com.netflix.hystrix.HystrixCommand.Setter.withGroupKey;
import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey;
import static org.assertj.core.api.BDDAssertions.then;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.ApplicationEventPublisher;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;

public class TraceCommandTests {

	static final long EXPECTED_TRACE_ID = 1L;
	TraceManager traceManager = new DefaultTraceManager(new AlwaysSampler(),
			Mockito.mock(ApplicationEventPublisher.class));

	@Before
	public void setup() {
		TraceContextHolder.removeCurrentTrace();
	}

	@After
	public void cleanup() {
		TraceContextHolder.removeCurrentTrace();
	}

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work()
			throws Exception {
		TraceContextHolder.removeCurrentTrace();
		Trace firstTraceFromHystrix = givenACommandWasExecuted(traceReturningCommand());

		Trace secondTraceFromHystrix = whenCommandIsExecuted(traceReturningCommand());

		then(secondTraceFromHystrix.getSpan().getTraceId()).as("second trace id")
				.isNotEqualTo(firstTraceFromHystrix.getSpan().getTraceId()).as("first trace id");
		then(secondTraceFromHystrix.getSaved()).as("saved trace as remnant of first trace")
				.isNull();
	}

	@Test
	public void should_run_Hystrix_command_with_span_passed_from_parent_thread() {
		givenATraceIsPresentInTheCurrentThread();
		TraceCommand<Trace> command = traceReturningCommand();

		Trace traceFromCommand = whenCommandIsExecuted(command);

		then(traceFromCommand).as("Trace from the Hystrix Thread").isNotNull();
		then(traceFromCommand.getSpan().getTraceId()).isEqualTo(EXPECTED_TRACE_ID);
	}

	@After
	public void cleanUpTrace() {
		TraceContextHolder.removeCurrentTrace();
	}

	private Trace givenATraceIsPresentInTheCurrentThread() {
		return this.traceManager.startSpan("test", MilliSpan.builder().traceId(EXPECTED_TRACE_ID).build());
	}

	private TraceCommand<Trace> traceReturningCommand() {
		return new TraceCommand<Trace>(this.traceManager,  withGroupKey(asKey(""))
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