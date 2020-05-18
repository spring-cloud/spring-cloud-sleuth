/*
 * Copyright 2013-2019 the original author or authors.
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import brave.Span;
import brave.Tracer;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.annotation.SleuthSpanCreatorAspectFluxTests.TestBean.TEST_STRING1;
import static org.springframework.cloud.sleuth.annotation.SleuthSpanCreatorAspectFluxTests.TestBean.TEST_STRING2;

@SpringBootTest(classes = SleuthSpanCreatorAspectFluxTests.TestConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class SleuthSpanCreatorAspectFluxTests {

	@Autowired
	TestBeanInterface testBean;

	@Autowired
	Tracer tracer;

	@Autowired
	TestSpanHandler spans;

	private static String toHexString(Long value) {
		then(value).isNotNull();
		return StringUtils.leftPad(Long.toHexString(value), 16, '0');
	}

	protected static Long id(Tracer tracer) {
		if (tracer.currentSpan() == null) {
			throw new IllegalStateException("Current Span is supposed to have a value!");
		}
		return tracer.currentSpan().context().spanId();
	}

	protected static Long id(Context context, Tracer tracer) {
		if (context.hasKey(TraceContext.class)) {
			return context.get(TraceContext.class).spanId();
		}
		return id(tracer);
	}

	@Before
	public void setup() {
		this.spans.clear();
		this.testBean.reset();
	}

	@Test
	public void shouldCreateSpanWhenAnnotationOnInterfaceMethod() {
		Flux<String> flux = this.testBean.testMethod();

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("test-method");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWhenAnnotationOnClassMethod() {
		Flux<String> flux = this.testBean.testMethod2();

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("test-method2");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnClassMethod() {
		Flux<String> flux = this.testBean.testMethod3();

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("custom-name-on-test-method3");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnInterfaceMethod() {
		Flux<String> flux = this.testBean.testMethod4();

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("custom-name-on-test-method4");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnInterfaceMethod() {
		// tag::execution[]
		Flux<String> flux = this.testBean.testMethod5("test");

		// end::execution[]
		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("custom-name-on-test-method5");
			then(this.spans.get(0).tags()).containsEntry("testTag", "test");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnClassMethod() {
		Flux<String> flux = this.testBean.testMethod6("test");

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("custom-name-on-test-method6");
			then(this.spans.get(0).tags()).containsEntry("testTag6", "test");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnInterfaceMethod() {
		Flux<String> flux = this.testBean.testMethod8("test");

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("custom-name-on-test-method8");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnClassMethod() {
		Flux<String> flux = this.testBean.testMethod9("test");

		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("custom-name-on-test-method9");
			then(this.spans.get(0).tags()).containsEntry("class", "TestBean")
					.containsEntry("method", "testMethod9");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldContinueSpanWithLogWhenAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			Flux<String> flux = this.testBean.testMethod10("test");

			verifyNoSpansUntilFluxComplete(flux);
		}
		finally {
			span.finish();
		}

		Awaitility.await().untilAsserted(() -> {
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("foo");
			then(spans.get(0).tags()).containsEntry("customTestTag10", "test");
			then(spans.get(0).annotations().stream().map(Map.Entry::getValue)
					.collect(Collectors.toList())).contains("customTest.before",
							"customTest.after");
			then(spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldStartAndCloseSpanOnContinueSpanIfSpanNotSet() {
		Flux<String> flux = this.testBean.testMethod10("test");
		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("test-method10");
			then(this.spans.get(0).tags()).containsEntry("customTestTag10", "test");
			then(this.spans.get(0).annotations().stream().map(Map.Entry::getValue)
					.collect(Collectors.toList())).contains("customTest.before",
							"customTest.after");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldContinueSpanWhenKeyIsUsedOnSpanTagWhenAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			Flux<String> flux = this.testBean.testMethod10_v2("test");

			verifyNoSpansUntilFluxComplete(flux);
		}
		finally {
			span.finish();
		}

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("foo");
			then(this.spans.get(0).tags()).containsEntry("customTestTag10", "test");
			then(this.spans.get(0).annotations().stream().map(Map.Entry::getValue)
					.collect(Collectors.toList())).contains("customTest.before",
							"customTest.after");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldContinueSpanWithLogWhenAnnotationOnClassMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			// tag::continue_span_execution[]
			Flux<String> flux = this.testBean.testMethod11("test");
			// end::continue_span_execution[]
			verifyNoSpansUntilFluxComplete(flux);
		}
		finally {
			span.finish();
		}

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("foo");
			then(this.spans.get(0).tags()).containsEntry("class", "TestBean")
					.containsEntry("method", "testMethod11")
					.containsEntry("customTestTag11", "test");
			then(this.spans.get(0).annotations().stream().map(Map.Entry::getValue)
					.collect(Collectors.toList())).contains("customTest.before",
							"customTest.after");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldAddErrorTagWhenExceptionOccurredInNewSpan() {
		try {
			Flux<String> flux = this.testBean.testMethod12("test");

			then(this.spans).isEmpty();

			flux.toIterable().iterator().next();
		}
		catch (RuntimeException ignored) {
		}

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("test-method12");
			then(this.spans.get(0).tags()).containsEntry("testTag12", "test")
					.containsEntry("error", "test exception 12");
			then(this.spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldAddErrorTagWhenExceptionOccurredInContinueSpan() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			// tag::continue_span_execution[]
			Flux<String> flux = this.testBean.testMethod13();

			then(this.spans).isEmpty();

			flux.toIterable().iterator().next();
			// end::continue_span_execution[]
		}
		catch (RuntimeException ignored) {
		}
		finally {
			span.finish();
		}

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("foo");
			then(this.spans.get(0).tags()).containsEntry("error", "test exception 13");
			then(this.spans.get(0).annotations().stream().map(Map.Entry::getValue)
					.collect(Collectors.toList())).contains("testMethod13.before",
							"testMethod13.afterFailure", "testMethod13.after");
			then(spans.get(0).finishTimestamp()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldNotCreateSpanWhenNotAnnotated() {
		Flux<String> flux = this.testBean.testMethod7();
		verifyNoSpansUntilFluxComplete(flux);

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).isEmpty();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromTraceContext() {
		Flux<Long> flux = this.testBean.newSpanInTraceContext();
		Long newSpanId = flux.blockFirst();

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("span-in-trace-context");
			then(this.spans.get(0).id()).isEqualTo(toHexString(newSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromSubscriberContext() {
		Flux<Long> flux = this.testBean.newSpanInSubscriberContext();
		Long newSpanId = flux.blockFirst();

		Awaitility.await().untilAsserted(() -> {
			then(this.spans).hasSize(1);
			then(this.spans.get(0).name()).isEqualTo("span-in-subscriber-context");
			then(this.spans.get(0).id()).isEqualTo(toHexString(newSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	private void verifyNoSpansUntilFluxComplete(Flux<String> flux) {
		Iterator<String> iterator = flux.toIterable().iterator();

		then(this.spans).isEmpty();
		this.testBean.proceed();

		String result1 = iterator.next();
		then(result1).isEqualTo(TEST_STRING1);
		then(this.spans).isEmpty();

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
		Flux<Long> newSpanInTraceContext();

		@NewSpan(name = "spanInSubscriberContext")
		Flux<Long> newSpanInSubscriberContext();

		void proceed();

		void reset();

	}

	protected static class TestBean implements TestBeanInterface {

		public static final String TEST_STRING1 = "Test String 1";

		public static final String TEST_STRING2 = "Test String 2";

		private final Tracer tracer;

		private AtomicReference<CompletableFuture<Void>> proceed = new AtomicReference<>(
				new CompletableFuture<>());

		private Flux<String> testFlux = Flux
				.defer(() -> Flux.just(TEST_STRING1, TEST_STRING2))
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
		public Flux<String> testMethod10_v2(
				@SpanTag(key = "customTestTag10") String param) {
			return this.testFlux;
		}

		@ContinueSpan(log = "customTest")
		@Override
		public Flux<String> testMethod11(@SpanTag("customTestTag11") String param) {
			return this.testFlux;
		}

		@Override
		public Flux<String> testMethod12(String param) {
			return Flux
					.defer(() -> Flux.error(new RuntimeException("test exception 12")));
		}

		@Override
		public Flux<String> testMethod13() {
			return Flux
					.defer(() -> Flux.error(new RuntimeException("test exception 13")));
		}

		@Override
		public Flux<String> testMethod14(String param) {
			return Flux.just(TEST_STRING1, TEST_STRING2);
		}

		@Override
		public Flux<Long> newSpanInTraceContext() {
			return Flux.defer(() -> Flux.just(id(this.tracer)));
		}

		@Override
		public Flux<Long> newSpanInSubscriberContext() {
			return Mono.subscriberContext()
					.flatMapMany(context -> Flux.just(id(context, this.tracer)));
		}

	}

	@Configuration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		@Bean
		public TestBeanInterface testBean(Tracer tracer) {
			return new TestBean(tracer);
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

}
