
package org.springframework.cloud.brave.instrument.web;

import java.util.concurrent.atomic.AtomicReference;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.sampler.Sampler;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.brave.SpanName;
import org.springframework.cloud.brave.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.brave.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit4.SpringRunner;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
		TraceAsyncIntegrationTests.TraceAsyncITestConfiguration.class },
		properties = "spring.sleuth.http.legacy.enabled=true")
public class TraceAsyncIntegrationTests {

	@Autowired
	ClassPerformingAsyncLogic classPerformingAsyncLogic;
	@Autowired
	Tracing tracing;
	@Autowired
	ArrayListSpanReporter reporter;

	@Before
	public void cleanup() {
		this.classPerformingAsyncLogic.clear();
	}

	@Test
	public void should_set_span_on_an_async_annotated_method() {
		whenAsyncProcessingTakesPlace();

		thenANewAsyncSpanGetsCreated();
	}

	@Test
	public void should_set_span_with_custom_method_on_an_async_annotated_method() {
		whenAsyncProcessingTakesPlaceWithCustomSpanName();

		thenAsyncSpanHasCustomName();
	}

	@Test
	public void should_continue_a_span_on_an_async_annotated_method() {
		Span span = givenASpanInCurrentThread();

		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			whenAsyncProcessingTakesPlace();
		} finally {
			span.finish();
		}

		thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOne(span);
	}

	@Test
	public void should_continue_a_span_with_custom_method_on_an_async_annotated_method() {
		Span span = givenASpanInCurrentThread();

		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			whenAsyncProcessingTakesPlaceWithCustomSpanName();
		} finally {
			span.finish();
		}

		thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOneAndSpanHasCustomName(span);
	}

	private Span givenASpanInCurrentThread() {
		return this.tracing.tracer().nextSpan().name("http:existing");
	}

	private void whenAsyncProcessingTakesPlace() {
		this.classPerformingAsyncLogic.invokeAsynchronousLogic();
	}

	private void whenAsyncProcessingTakesPlaceWithCustomSpanName() {
		this.classPerformingAsyncLogic.customNameInvokeAsynchronousLogic();
	}

	private void thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOne(final Span span) {
		Awaitility.await().atMost(5, SECONDS).untilAsserted(
				() -> {
					then(TraceAsyncIntegrationTests.this.classPerformingAsyncLogic
							.getSpan().context().traceId()).isEqualTo(span.context().traceId());
					then(this.reporter.getSpans()).hasSize(2);
					// HTTP
					then(this.reporter.getSpans().get(0).name()).isEqualTo("http:existing");
					// ASYNC
					then(this.reporter.getSpans().get(1).tags())
							.containsEntry("class", "ClassPerformingAsyncLogic")
							.containsEntry("method", "invokeAsynchronousLogic");
				});
	}

	private void thenANewAsyncSpanGetsCreated() {
		Awaitility.await().atMost(5, SECONDS).untilAsserted(
				() -> {
					then(this.reporter.getSpans()).hasSize(1);
					zipkin2.Span storedSpan = this.reporter.getSpans().get(0);
					then(storedSpan.name()).isEqualTo("invoke-asynchronous-logic");
					then(storedSpan.tags())
							.containsEntry("class", "ClassPerformingAsyncLogic")
							.containsEntry("method", "invokeAsynchronousLogic");
				});
	}

	private void thenTraceIdIsPassedFromTheCurrentThreadToTheAsyncOneAndSpanHasCustomName(final Span span) {
		Awaitility.await().atMost(5, SECONDS).untilAsserted(
				() -> {
					then(TraceAsyncIntegrationTests.this.classPerformingAsyncLogic
							.getSpan().context().traceId()).isEqualTo(span.context().traceId());
					then(this.reporter.getSpans()).hasSize(2);
					// HTTP
					then(this.reporter.getSpans().get(0).name()).isEqualTo("http:existing");
					// ASYNC
					then(this.reporter.getSpans().get(1).tags())
							.containsEntry("class", "ClassPerformingAsyncLogic")
							.containsEntry("method", "customNameInvokeAsynchronousLogic");
				});
	}

	private void thenAsyncSpanHasCustomName() {
		Awaitility.await().atMost(5, SECONDS).untilAsserted(
				() -> {
					then(this.reporter.getSpans()).hasSize(1);
					zipkin2.Span storedSpan = this.reporter.getSpans().get(0);
					then(storedSpan.name()).isEqualTo("foo");
					then(storedSpan.tags())
							.containsEntry("class", "ClassPerformingAsyncLogic")
							.containsEntry("method", "customNameInvokeAsynchronousLogic");
				});
	}

	@After
	public void cleanTrace(){
		this.reporter.clear();
	}

	@DefaultTestAutoConfiguration
	@EnableAsync
	@Configuration
	static class TraceAsyncITestConfiguration {

		@Bean
		ClassPerformingAsyncLogic asyncClass(Tracing tracing) {
			return new ClassPerformingAsyncLogic(tracing);
		}

		@Bean
		Sampler defaultSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		ArrayListSpanReporter reporter() {
			return new ArrayListSpanReporter();
		}

	}

	static class ClassPerformingAsyncLogic {

		AtomicReference<Span> span = new AtomicReference<>();

		private final Tracing tracing;

		ClassPerformingAsyncLogic(Tracing tracing) {
			this.tracing = tracing;
		}

		@Async
		public void invokeAsynchronousLogic() {
			this.span.set(this.tracing.tracer().currentSpan());
		}

		@Async
		@SpanName("foo")
		public void customNameInvokeAsynchronousLogic() {
			this.span.set(this.tracing.tracer().currentSpan());
		}

		public Span getSpan() {
			return this.span.get();
		}

		public void clear() {
			this.span.set(null);
		}
	}
}
