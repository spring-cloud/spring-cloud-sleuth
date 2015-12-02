package org.springframework.cloud.sleuth.instrument.hystrix;

import static com.netflix.hystrix.HystrixCommand.Setter.withGroupKey;
import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey;
import static org.assertj.core.api.BDDAssertions.then;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.sampler.IsTracingSampler;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.JdkIdGenerator;

public class TraceCommandTest {

	static final String EXPECTED_TRACE_ID = "A";
	TraceManager traceManager = new DefaultTraceManager(new IsTracingSampler(), new JdkIdGenerator(), Mockito.mock(ApplicationEventPublisher.class));

	@Test
	@Ignore("Will fail cause the parent command is not passed. Uncomment lines in TraceCommand to make it pass")
	public void should_run_Hystrix_command_with_span_passed_from_parent_thread() {
		Trace trace = givenATraceIsPresentInTheCurrentThread();
		TraceCommand<Trace> command = givenATraceReturningCommand();

		Trace traceFromCommand = whenCommandIsExecuted(command);

		then(traceFromCommand).as("Trace from the Hystrix Thread").isNotNull();
		then(traceFromCommand.getSpan().getTraceId()).isEqualTo(EXPECTED_TRACE_ID);
		traceManager.close(trace);
	}

	@After
	public void cleanUpTrace() {
		TraceContextHolder.removeCurrentTrace();
	}

	private Trace givenATraceIsPresentInTheCurrentThread() {
		return traceManager.startSpan("test", MilliSpan.builder().traceId(EXPECTED_TRACE_ID).build());
	}

	private TraceCommand<Trace> givenATraceReturningCommand() {
		return new TraceCommand<Trace>(traceManager,  withGroupKey(asKey(""))) {
			@Override
			public Trace doRun() throws Exception {
				return TraceContextHolder.getCurrentTrace();
			}
		};
	}

	private Trace whenCommandIsExecuted(TraceCommand<Trace> command) {
		return command.execute();
	}
}