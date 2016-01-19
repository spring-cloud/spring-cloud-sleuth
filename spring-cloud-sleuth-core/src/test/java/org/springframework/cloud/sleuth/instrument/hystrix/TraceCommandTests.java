package org.springframework.cloud.sleuth.instrument.hystrix;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.SpanContextHolder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Random;

import static com.netflix.hystrix.HystrixCommand.Setter.withGroupKey;
import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey;
import static org.assertj.core.api.BDDAssertions.then;

public class TraceCommandTests {

	static final long EXPECTED_TRACE_ID = 1L;
	Tracer tracer = new DefaultTracer(new AlwaysSampler(),
			new Random(), Mockito.mock(ApplicationEventPublisher.class));

	@Before
	public void setup() {
		SpanContextHolder.removeCurrentSpan();
	}

	@After
	public void cleanup() {
		SpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work()
			throws Exception {
		SpanContextHolder.removeCurrentSpan();
		Span firstSpanFromHystrix = givenACommandWasExecuted(traceReturningCommand());

		Span secondSpanFromHystrix = whenCommandIsExecuted(traceReturningCommand());

		then(secondSpanFromHystrix.getTraceId()).as("second span id")
				.isNotEqualTo(firstSpanFromHystrix.getTraceId()).as("first span id");
		then(secondSpanFromHystrix.getSavedSpan()).as("saved span as remnant of first span")
				.isNull();
	}

	@Test
	public void should_run_Hystrix_command_with_span_passed_from_parent_thread() {
		givenATraceIsPresentInTheCurrentThread();
		TraceCommand<Span> command = traceReturningCommand();

		Span spanFromCommand = whenCommandIsExecuted(command);

		then(spanFromCommand).as("Span from the Hystrix Thread").isNotNull();
		then(spanFromCommand.getTraceId()).isEqualTo(EXPECTED_TRACE_ID);
	}

	@After
	public void cleanUpTrace() {
		SpanContextHolder.removeCurrentSpan();
	}

	private Span givenATraceIsPresentInTheCurrentThread() {
		return this.tracer
				.joinTrace("test", Span.builder().traceId(EXPECTED_TRACE_ID).build());
	}

	private TraceCommand<Span> traceReturningCommand() {
		return new TraceCommand<Span>(this.tracer,  withGroupKey(asKey(""))
				.andCommandKey(HystrixCommandKey.Factory.asKey("")).andThreadPoolPropertiesDefaults(
						HystrixThreadPoolProperties.Setter().withMaxQueueSize(1).withCoreSize(1))) {
			@Override
			public Span doRun() throws Exception {
				return SpanContextHolder.getCurrentSpan();
			}
		};
	}

	private Span whenCommandIsExecuted(TraceCommand<Span> command) {
		return command.execute();
	}

	private Span givenACommandWasExecuted(TraceCommand<Span> command) {
		return whenCommandIsExecuted(command);
	}
}