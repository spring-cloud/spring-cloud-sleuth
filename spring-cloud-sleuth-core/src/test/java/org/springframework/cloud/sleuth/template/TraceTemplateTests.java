package org.springframework.cloud.sleuth.template;

import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Random;

import static org.assertj.core.api.BDDAssertions.then;

public class TraceTemplateTests {

	Tracer tracer = new DefaultTracer(new AlwaysSampler(),
			new Random(), Mockito.mock(ApplicationEventPublisher.class));

	@After
	public void close() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_pass_trace_to_the_callback_if_tracing_is_active() {
		Span initialSpan = this.tracer.startTrace("test");
		TraceTemplate traceTemplate = new TraceTemplate(this.tracer);

		Span spanFromCallback = whenTraceCallbackReturningCurrentTraceIsExecuted(traceTemplate);

		then(spanFromCallback).isNotNull();
		then(spanFromCallback.getTraceId()).isEqualTo(initialSpan.getTraceId());
	}

	private Span whenTraceCallbackReturningCurrentTraceIsExecuted(TraceTemplate traceTemplate) {
		return traceTemplate.trace(new TraceCallback<Span>() {
				@Override
				public Span doInTrace(Span span) {
					return TestSpanContextHolder.getCurrentSpan();
				}
			});
	}

}