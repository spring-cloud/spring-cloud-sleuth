
package org.springframework.cloud.sleuth.instrument.web;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.jayway.awaitility.Awaitility;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {
		TraceAsyncITest.TraceAsyncITestConfiguration.class })
public class TraceAsyncITest {

	@Autowired ClassPerformingAsyncLogic classPerformingAsyncLogic;
	@Autowired TraceManager traceManager;

	@Test
	public void should_set_span_on_an_async_annotated_method() {
		Span span = givenASpanInCurrentThread();

		whenAsyncProcessingTakesPlace();

		thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOne(span);
	}

	private Span givenASpanInCurrentThread() {
		Span span = traceManager.startSpan("existing").getSpan();
		traceManager.continueSpan(span);
		return span;
	}

	private void whenAsyncProcessingTakesPlace() {
		classPerformingAsyncLogic.invokeAsynchronousLogic();
	}

	private void thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOne(final Span span) {
		Awaitility.await().until(new Runnable() {
			@Override
			public void run() {
				then(span)
						.hasTraceIdEqualTo(classPerformingAsyncLogic.getTraceId())
						.hasNameNotEqualTo(classPerformingAsyncLogic.getSpanName());
			}
		});
	}

	@After
	public void cleanTrace() {
		TraceContextHolder.removeCurrentTrace();
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

		AtomicReference<Span> span;

		@Async
		public void invokeAsynchronousLogic() {
			span = new AtomicReference<>(TraceContextHolder.getCurrentSpan());
		}

		public String getTraceId() {
			if (span == null || (span.get() != null
					&& span.get().getTraceId() == null)) {
				return null;
			}
			return span.get().getTraceId();
		}

		public String getSpanName() {
			if (span == null
					|| (span.get() != null && span.get().getName() == null)) {
				return null;
			}
			return span.get().getName();
		}
	}
}
