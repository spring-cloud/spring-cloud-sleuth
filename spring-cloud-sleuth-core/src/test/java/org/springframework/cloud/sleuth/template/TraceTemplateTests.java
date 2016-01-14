package org.springframework.cloud.sleuth.template;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.cloud.sleuth.util.RandomLongSpanIdGenerator;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.BDDAssertions.then;

public class TraceTemplateTests {

	TraceManager traceManager = new DefaultTraceManager(new AlwaysSampler(), new RandomLongSpanIdGenerator(), Mockito.mock(ApplicationEventPublisher.class));

	@Test
	public void should_pass_trace_to_the_callback_if_tracing_is_active() {
		Trace initialTrace = traceManager.startSpan("test");
		TraceTemplate traceTemplate = new TraceTemplate(traceManager);

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