package org.springframework.cloud.sleuth.template;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Random;

import static org.assertj.core.api.BDDAssertions.then;

public class TraceTemplateTests {

	Tracer tracer = new DefaultTracer(new AlwaysSampler(),
			new Random(), Mockito.mock(ApplicationEventPublisher.class));

	@Test
	public void should_pass_trace_to_the_callback_if_tracing_is_active() {
		Trace initialTrace = tracer.startTrace("test");
		TraceTemplate traceTemplate = new TraceTemplate(tracer);

		Trace traceFromCallback = whenTraceCallbackReturningCurrentTraceIsExecuted(traceTemplate);

		then(traceFromCallback).isNotNull();
		then(traceFromCallback.getSpan().getTraceId()).isEqualTo(initialTrace.getSpan().getTraceId());
	}

	private Trace whenTraceCallbackReturningCurrentTraceIsExecuted(TraceTemplate traceTemplate) {
		return traceTemplate.trace(new TraceCallback<Trace>() {
				@Override
				public Trace doInTrace(Trace trace) {
					return TraceContextHolder.getCurrentTrace();
				}
			});
	}

}