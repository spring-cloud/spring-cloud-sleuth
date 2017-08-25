
package org.springframework.cloud.sleuth.instrument.web;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit4.SpringRunner;

import org.awaitility.Awaitility;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
		TraceAsyncIntegrationTests.TraceAsyncITestConfiguration.class })
public class TraceAsyncIntegrationTests {

	@Autowired
	ClassPerformingAsyncLogic classPerformingAsyncLogic;
	@Autowired
	Tracer tracer;

	@Before
	public void cleanup() {
		this.classPerformingAsyncLogic.clear();
	}

	@Test
	public void should_set_span_on_an_async_annotated_method() {
		Span span = givenASpanInCurrentThread();

		whenAsyncProcessingTakesPlace();

		thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOne(span);
		this.tracer.close(span);
	}

	@Test
	public void should_set_span_with_custom_method_on_an_async_annotated_method() {
		Span span = givenASpanInCurrentThread();

		whenAsyncProcessingTakesPlaceWithCustomSpanName();

		thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOneAndSpanHasCustomName(span);
		this.tracer.close(span);
	}

	private Span givenASpanInCurrentThread() {
		return this.tracer.createSpan("http:existing");
	}

	private void whenAsyncProcessingTakesPlace() {
		this.classPerformingAsyncLogic.invokeAsynchronousLogic();
	}

	private void whenAsyncProcessingTakesPlaceWithCustomSpanName() {
		this.classPerformingAsyncLogic.customNameInvokeAsynchronousLogic();
	}

	private void thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOne(final Span span) {
		Awaitility.await().atMost(5, SECONDS).untilAsserted(
				() -> then(TraceAsyncIntegrationTests.this.classPerformingAsyncLogic.getSpan())
					.hasTraceIdEqualTo(span.getTraceId())
					.hasNameEqualTo("invoke-asynchronous-logic")
					.isALocalComponentSpan()
					.hasATag("class", "ClassPerformingAsyncLogic")
					.hasATag("method", "invokeAsynchronousLogic"));
	}

	private void thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOneAndSpanHasCustomName(final Span span) {
		Awaitility.await().atMost(5, SECONDS).untilAsserted(
				() -> then(TraceAsyncIntegrationTests.this.classPerformingAsyncLogic.getSpan())
					.hasTraceIdEqualTo(span.getTraceId())
					.hasNameEqualTo("foo")
					.isALocalComponentSpan()
					.hasATag("class", "ClassPerformingAsyncLogic")
					.hasATag("method", "customNameInvokeAsynchronousLogic"));
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

		@Async
		@SpanName("foo")
		public void customNameInvokeAsynchronousLogic() {
			this.span.set(TestSpanContextHolder.getCurrentSpan());
		}

		public Span getSpan() {
			return this.span.get();
		}

		public void clear() {
			this.span.set(null);
		}
	}
}
