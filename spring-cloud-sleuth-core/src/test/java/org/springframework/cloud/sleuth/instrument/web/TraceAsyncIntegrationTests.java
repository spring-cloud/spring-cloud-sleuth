
package org.springframework.cloud.sleuth.instrument.web;

import com.jayway.awaitility.Awaitility;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.trace.SpanContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {
		TraceAsyncIntegrationTests.TraceAsyncITestConfiguration.class })
public class TraceAsyncIntegrationTests {

	@Autowired ClassPerformingAsyncLogic classPerformingAsyncLogic;
	@Autowired
	Tracer tracer;

	@Test
	public void should_set_span_on_an_async_annotated_method() {
		Span span = givenASpanInCurrentThread();

		whenAsyncProcessingTakesPlace();

		thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOne(span);
	}

	private Span givenASpanInCurrentThread() {
		Span span = this.tracer.startTrace("existing");
		this.tracer.continueSpan(span);
		return span;
	}

	private void whenAsyncProcessingTakesPlace() {
		this.classPerformingAsyncLogic.invokeAsynchronousLogic();
	}

	private void thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOne(final Span span) {
		Awaitility.await().until(new Runnable() {
			@Override
			public void run() {
				then(span)
						.hasTraceIdEqualTo(TraceAsyncIntegrationTests.this.classPerformingAsyncLogic.getTraceId())
						.hasNameNotEqualTo(TraceAsyncIntegrationTests.this.classPerformingAsyncLogic.getSpanName());
			}
		});
	}

	@After
	public void cleanTrace() {
		SpanContextHolder.removeCurrentSpan();
	}

	@DefaultTestAutoConfiguration
	@EnableAsync
	@Configuration
	static class TraceAsyncITestConfiguration {

		@Bean
		ClassPerformingAsyncLogic asyncClass() {
			return new ClassPerformingAsyncLogic();
		}

	}

	static class ClassPerformingAsyncLogic {

		AtomicReference<Span> span = new AtomicReference<>();

		@Async
		public void invokeAsynchronousLogic() {
			this.span.set(SpanContextHolder.getCurrentSpan());
		}

		public Long getTraceId() {
			if (this.span.get() == null) {
				return null;
			}
			return this.span.get().getTraceId();
		}

		public String getSpanName() {
			if (this.span.get() != null && this.span.get().getName() == null) {
				return null;
			}
			return this.span.get().getName();
		}
	}
}
