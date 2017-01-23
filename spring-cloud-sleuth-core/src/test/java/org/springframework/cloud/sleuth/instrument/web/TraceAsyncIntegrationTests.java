
package org.springframework.cloud.sleuth.instrument.web;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit4.SpringRunner;

import com.jayway.awaitility.Awaitility;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
		TraceAsyncIntegrationTests.TraceAsyncITestConfiguration.class },
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TraceAsyncIntegrationTests {

	@Autowired ClassPerformingAsyncLogic classPerformingAsyncLogic;
	@Autowired Tracer tracer;

	@Test
	public void should_set_span_on_an_async_annotated_method() {
		Span span = givenASpanInCurrentThread();

		whenAsyncProcessingTakesPlace();

		thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOne(span);
	}

	private Span givenASpanInCurrentThread() {
		return this.tracer.createSpan("http:existing");
	}

	private void whenAsyncProcessingTakesPlace() {
		this.classPerformingAsyncLogic.invokeAsynchronousLogic();
	}

	private void thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOne(final Span span) {
		Awaitility.await().until(new Runnable() {
			@Override
			public void run() {
				then(TraceAsyncIntegrationTests.this.classPerformingAsyncLogic.getSpan())
						.hasTraceIdEqualTo(span.getTraceId())
						.hasNameEqualTo("invoke-asynchronous-logic")
						.isALocalComponentSpan()
						.hasATag("class", "ClassPerformingAsyncLogic")
						.hasATag("method", "invokeAsynchronousLogic");
			}
		});
	}

	@After
	public void cleanTrace() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@DefaultTestAutoConfiguration
	@EnableAsync
	@Configuration
	static class TraceAsyncITestConfiguration {

		@Bean
		ClassPerformingAsyncLogic asyncClass() {
			return new ClassPerformingAsyncLogic();
		}

		@Bean
		Sampler defaultSampler() {
			return new AlwaysSampler();
		}

	}

	static class ClassPerformingAsyncLogic {

		AtomicReference<Span> span = new AtomicReference<>();

		@Async
		public void invokeAsynchronousLogic() {
			this.span.set(TestSpanContextHolder.getCurrentSpan());
		}

		public Span getSpan() {
			return this.span.get();
		}
	}
}
