/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.annotation;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.annotation.SleuthSpanCreatorAspectFluxTests.TestBean.TEST_STRING1;
import static org.springframework.cloud.sleuth.annotation.SleuthSpanCreatorAspectFluxTests.TestBean.TEST_STRING2;

@ContextConfiguration(classes = SleuthSpanCreatorAspectFluxTests.TestConfiguration.class)
public abstract class SleuthSpanCreatorAspectFluxTests {

	@Autowired
	TestBeanInterface testBean;

	@Autowired
	CurrentTraceContext currentTraceContext;

	@Autowired
	Tracer tracer;

	@Autowired
	TestSpanHandler spans;

	TraceContext context = traceContext();

	public abstract TraceContext traceContext();

	protected static String id(Tracer tracer) {
		if (tracer.currentSpan() == null) {
			throw new IllegalStateException("Current Span is supposed to have a value!");
		}
		return tracer.currentSpan().context().spanId();
	}

	protected static String id(Context context, Tracer tracer) {
		if (context.hasKey(TraceContext.class)) {
			return context.get(TraceContext.class).spanId();
		}
		return id(tracer);
	}

	@BeforeEach
	public void setup() {
		this.spans.clear();
		this.testBean.reset();
	}

	@Test
	public void newSpan_shouldContinueExistingTrace() {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.newScope(context)) {
			Flux<String> flux = this.testBean.testMethod();
			verifyNoSpansUntilFluxComplete(flux);
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getTraceId()).isEqualTo(context.traceId());
			BDDAssertions.then(this.spans.get(0).getParentId()).isEqualTo(context.spanId());
		});
	}

	@Test
	public void shouldCreateSpanWhenAnnotationOnInterfaceMethod() {
		Flux<String> flux = this.testBean.testMethod();

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("test-method");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWhenAnnotationOnClassMethod() {
		Flux<String> flux = this.testBean.testMethod2();

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("test-method2");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnClassMethod() {
		Flux<String> flux = this.testBean.testMethod3();

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method3");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnInterfaceMethod() {
		Flux<String> flux = this.testBean.testMethod4();

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method4");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnInterfaceMethod() {
		// tag::execution[]
		Flux<String> flux = this.testBean.testMethod5("test");

		// end::execution[]
		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method5");
			BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("testTag", "test");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnClassMethod() {
		Flux<String> flux = this.testBean.testMethod6("test");

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method6");
			BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("testTag6", "test");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnInterfaceMethod() {
		Flux<String> flux = this.testBean.testMethod8("test");

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method8");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnClassMethod() {
		Flux<String> flux = this.testBean.testMethod9("test");

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method9");
			BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("class", "TestBean").containsEntry("method",
					"testMethod9");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldContinueSpanWithLogWhenAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			Flux<String> flux = this.testBean.testMethod10("test");

			verifyNoSpansUntilFluxComplete(flux);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(spans).hasSize(1);
			BDDAssertions.then(spans.get(0).getName()).isEqualTo("foo");
			BDDAssertions.then(spans.get(0).getTags()).containsEntry("customTestTag10", "test");
			BDDAssertions.then(spans.get(0).getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
					.contains("customTest.before", "customTest.after");
			BDDAssertions.then(spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldStartAndCloseSpanOnContinueSpanIfSpanNotSet() {
		Flux<String> flux = this.testBean.testMethod10("test");
		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("test-method10");
			BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("customTestTag10", "test");
			BDDAssertions
					.then(this.spans.get(0).getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
					.contains("customTest.before", "customTest.after");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldContinueSpanWhenKeyIsUsedOnSpanTagWhenAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			Flux<String> flux = this.testBean.testMethod10_v2("test");

			verifyNoSpansUntilFluxComplete(flux);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("foo");
			BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("customTestTag10", "test");
			BDDAssertions
					.then(this.spans.get(0).getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
					.contains("customTest.before", "customTest.after");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldContinueSpanWithLogWhenAnnotationOnClassMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			// tag::continue_span_execution[]
			Flux<String> flux = this.testBean.testMethod11("test");
			// end::continue_span_execution[]
			verifyNoSpansUntilFluxComplete(flux);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("foo");
			BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("class", "TestBean")
					.containsEntry("method", "testMethod11").containsEntry("customTestTag11", "test");
			BDDAssertions
					.then(this.spans.get(0).getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
					.contains("customTest.before", "customTest.after");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldAddErrorTagWhenExceptionOccurredInNewSpan() {
		try {
			Flux<String> flux = this.testBean.testMethod12("test");

			BDDAssertions.then(this.spans).isEmpty();

			flux.toIterable().iterator().next();
		}
		catch (RuntimeException ignored) {
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			FinishedSpan finishedSpan = this.spans.get(0);
			BDDAssertions.then(finishedSpan.getName()).isEqualTo("test-method12");
			BDDAssertions.then(finishedSpan.getTags()).containsEntry("testTag12", "test");
			BDDAssertions.then(finishedSpan.getError()).hasMessageContaining("test exception 12");
			BDDAssertions.then(finishedSpan.getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldAddErrorTagWhenExceptionOccurredInContinueSpan() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			// tag::continue_span_execution[]
			Flux<String> flux = this.testBean.testMethod13();

			BDDAssertions.then(this.spans).isEmpty();

			flux.toIterable().iterator().next();
			// end::continue_span_execution[]
		}
		catch (RuntimeException ignored) {
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("foo");
			BDDAssertions.then(this.spans.get(0).getError()).hasMessageContaining("test exception 13");
			BDDAssertions
					.then(this.spans.get(0).getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
					.contains("testMethod13.before", "testMethod13.afterFailure", "testMethod13.after");
			BDDAssertions.then(spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldNotCreateSpanWhenNotAnnotated() {
		Flux<String> flux = this.testBean.testMethod7();
		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).isEmpty();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromTraceContext() {
		Flux<String> flux = this.testBean.newSpanInTraceContext();
		String newSpanId = flux.blockFirst();

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("span-in-trace-context");
			BDDAssertions.then(this.spans.get(0).getSpanId()).isEqualTo(newSpanId);
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromSubscriberContext() {
		Flux<String> flux = this.testBean.newSpanInSubscriberContext();
		String newSpanId = flux.blockFirst();

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("span-in-subscriber-context");
			BDDAssertions.then(this.spans.get(0).getSpanId()).isEqualTo(newSpanId);
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	private void verifyNoSpansUntilFluxComplete(Flux<String> flux) {
		Iterator<String> iterator = flux.toIterable().iterator();

		BDDAssertions.then(this.spans).isEmpty();
		this.testBean.proceed();

		String result1 = iterator.next();
		then(result1).isEqualTo(TEST_STRING1);
		BDDAssertions.then(this.spans).isEmpty();

		this.testBean.proceed();
		String result2 = iterator.next();

		then(result2).isEqualTo(TEST_STRING2);
	}

	protected interface TestBeanInterface {

		// tag::annotated_method[]
		@NewSpan
		Flux<String> testMethod();
		// end::annotated_method[]

		Flux<String> testMethod2();

		@NewSpan(name = "interfaceCustomNameOnTestMethod3")
		Flux<String> testMethod3();

		// tag::custom_name_on_annotated_method[]
		@NewSpan("customNameOnTestMethod4")
		Flux<String> testMethod4();
		// end::custom_name_on_annotated_method[]

		// tag::custom_name_and_tag_on_annotated_method[]
		@NewSpan(name = "customNameOnTestMethod5")
		Flux<String> testMethod5(@SpanTag("testTag") String param);
		// end::custom_name_and_tag_on_annotated_method[]

		Flux<String> testMethod6(String test);

		Flux<String> testMethod7();

		@NewSpan(name = "customNameOnTestMethod8")
		Flux<String> testMethod8(String param);

		@NewSpan(name = "testMethod9")
		Flux<String> testMethod9(String param);

		@ContinueSpan(log = "customTest")
		Flux<String> testMethod10(@SpanTag("testTag10") String param);

		@ContinueSpan(log = "customTest")
		Flux<String> testMethod10_v2(@SpanTag(key = "testTag10") String param);

		// tag::continue_span[]
		@ContinueSpan(log = "testMethod11")
		Flux<String> testMethod11(@SpanTag("testTag11") String param);
		// end::continue_span[]

		@NewSpan
		Flux<String> testMethod12(@SpanTag("testTag12") String param);

		@ContinueSpan(log = "testMethod13")
		Flux<String> testMethod13();

		@ContinueSpan
		Flux<String> testMethod14(String param);

		@NewSpan(name = "spanInTraceContext")
		Flux<String> newSpanInTraceContext();

		@NewSpan(name = "spanInSubscriberContext")
		Flux<String> newSpanInSubscriberContext();

		void proceed();

		void reset();

	}

	protected static class TestBean implements TestBeanInterface {

		public static final String TEST_STRING1 = "Test String 1";

		public static final String TEST_STRING2 = "Test String 2";

		private final Tracer tracer;

		private AtomicReference<CompletableFuture<Void>> proceed = new AtomicReference<>(new CompletableFuture<>());

		private Flux<String> testFlux = Flux.defer(() -> Flux.just(TEST_STRING1, TEST_STRING2))
				.delayUntil(s -> Mono.fromFuture(this.proceed.get()))
				.doOnNext(s -> this.proceed.set(new CompletableFuture<>()));

		public TestBean(Tracer tracer) {
			this.tracer = tracer;
		}

		@Override
		public void reset() {
			this.proceed.set(new CompletableFuture<>());
		}

		public void proceed() {
			this.proceed.get().complete(null);
		}

		@Override
		public Flux<String> testMethod() {
			return this.testFlux;
		}

		@NewSpan
		@Override
		public Flux<String> testMethod2() {
			return this.testFlux;
		}

		// tag::name_on_implementation[]
		@NewSpan(name = "customNameOnTestMethod3")
		@Override
		public Flux<String> testMethod3() {
			return this.testFlux;
		}
		// end::name_on_implementation[]

		@Override
		public Flux<String> testMethod4() {
			return this.testFlux;
		}

		@Override
		public Flux<String> testMethod5(String test) {
			return this.testFlux;
		}

		@NewSpan(name = "customNameOnTestMethod6")
		@Override
		public Flux<String> testMethod6(@SpanTag("testTag6") String test) {
			return this.testFlux;
		}

		@Override
		public Flux<String> testMethod7() {
			return this.testFlux;
		}

		@Override
		public Flux<String> testMethod8(String param) {
			return this.testFlux;
		}

		@NewSpan(name = "customNameOnTestMethod9")
		@Override
		public Flux<String> testMethod9(String param) {
			return this.testFlux;
		}

		@Override
		public Flux<String> testMethod10(@SpanTag("customTestTag10") String param) {
			return this.testFlux;
		}

		@Override
		public Flux<String> testMethod10_v2(@SpanTag(key = "customTestTag10") String param) {
			return this.testFlux;
		}

		@ContinueSpan(log = "customTest")
		@Override
		public Flux<String> testMethod11(@SpanTag("customTestTag11") String param) {
			return this.testFlux;
		}

		@Override
		public Flux<String> testMethod12(String param) {
			return Flux.defer(() -> Flux.error(new RuntimeException("test exception 12")));
		}

		@Override
		public Flux<String> testMethod13() {
			return Flux.defer(() -> Flux.error(new RuntimeException("test exception 13")));
		}

		@Override
		public Flux<String> testMethod14(String param) {
			return Flux.just(TEST_STRING1, TEST_STRING2);
		}

		@Override
		public Flux<String> newSpanInTraceContext() {
			return Flux.defer(() -> Flux.just(id(this.tracer)));
		}

		@Override
		public Flux<String> newSpanInSubscriberContext() {
			return Mono.subscriberContext().flatMapMany(context -> Flux.just(id(context, this.tracer)));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	public static class TestConfiguration {

		@Bean
		public TestBeanInterface testBean(Tracer tracer) {
			return new TestBean(tracer);
		}

	}

}
