package org.springframework.cloud.sleuth.instrument.async;

import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.assertj.core.api.BDDAssertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

/**
 * @author Marcin Grzejszczak
 */
public class TraceAsyncAspectTest {

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(new StrictCurrentTraceContext())
			.spanReporter(this.reporter)
			.build();
	ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);

	@Before
	public void setup() throws NoSuchMethodException {
		MethodSignature signature = Mockito.mock(MethodSignature.class);
		BDDMockito.given(signature.getName()).willReturn("fooBar");
		BDDMockito.given(signature.getMethod()).willReturn(TraceAsyncAspectTest.class.getMethod("setup"));
		BDDMockito.given(this.point.getSignature()).willReturn(signature);
		BDDMockito.given(this.point.getTarget()).willReturn("");
	}

	//Issue#926
	@Test public void should_work() throws Throwable {
		TraceAsyncAspect asyncAspect = new TraceAsyncAspect(this.tracing.tracer(),
				new DefaultSpanNamer()) {
			@Override String name(ProceedingJoinPoint pjp) {
				return "foo-bar";
			}
		};

		asyncAspect.traceBackgroundThread(this.point);

		BDDAssertions.then(this.reporter.getSpans()).hasSize(1);
		BDDAssertions.then(this.reporter.getSpans().get(0).name()).isEqualTo("foo-bar");
	}
}