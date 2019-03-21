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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import brave.Span;
import brave.Tracer;
import brave.sampler.Sampler;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Mono;
import zipkin2.Annotation;
import zipkin2.reporter.Reporter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.util.Pair;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.annotation.SleuthSpanCreatorAspectMonoTests.TestBean.TEST_STRING;
import static org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD;
import static reactor.core.publisher.Mono.just;

@SpringBootTest(classes = SleuthSpanCreatorAspectMonoTests.TestConfiguration.class)
@RunWith(SpringRunner.class)
@DirtiesContext(methodMode = BEFORE_METHOD)
public class SleuthSpanCreatorAspectMonoTests {

	@Autowired
	TestBeanInterface testBean;

	@Autowired
	TestBeanOuter testBeanOuter;

	@Autowired
	Tracer tracer;

	@Autowired
	ArrayListSpanReporter reporter;

	private static String toHexString(long value) {
		return StringUtils.leftPad(Long.toHexString(value), 16, '0');
	}

	protected static Long id(Tracer tracer) {
		if (tracer.currentSpan() == null) {
			throw new IllegalStateException("Current Span is supposed to have a value!");
		}
		return tracer.currentSpan().context().spanId();
	}

	@Before
	public void setup() {
		this.reporter.clear();
	}

	@Test
	public void shouldCreateSpanWhenAnnotationOnInterfaceMethod() {
		Mono<String> mono = this.testBean.testMethod();

		then(this.reporter.getSpans()).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("test-method");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWhenAnnotationOnClassMethod() {
		Mono<String> mono = this.testBean.testMethod2();

		then(this.reporter.getSpans()).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("test-method2");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnClassMethod() {
		Mono<String> mono = this.testBean.testMethod3();

		then(this.reporter.getSpans()).isEmpty();

		String result = mono.block();

		Awaitility.await().untilAsserted(() -> {
			then(result).isEqualTo(TEST_STRING);
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("custom-name-on-test-method3");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnInterfaceMethod() {
		Mono<String> mono = this.testBean.testMethod4();

		then(this.reporter.getSpans()).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("custom-name-on-test-method4");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnInterfaceMethod() {
		// tag::execution[]
		Mono<String> mono = this.testBean.testMethod5("test");

		// end::execution[]
		then(this.reporter.getSpans()).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("custom-name-on-test-method5");
			then(spans.get(0).tags()).containsEntry("testTag", "test");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnClassMethod() {
		Mono<String> mono = this.testBean.testMethod6("test");

		then(this.reporter.getSpans()).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("custom-name-on-test-method6");
			then(spans.get(0).tags()).containsEntry("testTag6", "test");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnInterfaceMethod() {
		Mono<String> mono = this.testBean.testMethod8("test");

		then(this.reporter.getSpans()).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("custom-name-on-test-method8");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnClassMethod() {
		Mono<String> mono = this.testBean.testMethod9("test");

		then(this.reporter.getSpans()).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("custom-name-on-test-method9");
			then(spans.get(0).tags()).containsEntry("class", "TestBean")
					.containsEntry("method", "testMethod9");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldContinueSpanWithLogWhenAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			Mono<String> mono = this.testBean.testMethod10("test");

			then(this.reporter.getSpans()).isEmpty();

			mono.block();
		}
		finally {
			span.finish();
		}

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("foo");
			then(spans.get(0).tags()).containsEntry("customTestTag10", "test");
			then(spans.get(0).annotations().stream().map(Annotation::value)
					.collect(Collectors.toList())).contains("customTest.before",
							"customTest.after");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldStartAndCloseSpanOnContinueSpanIfSpanNotSet() {
		this.testBean.testMethod10("test").block();

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("test-method10");
			then(spans.get(0).tags()).containsEntry("customTestTag10", "test");
			then(spans.get(0).annotations().stream().map(Annotation::value)
					.collect(Collectors.toList())).contains("customTest.before",
							"customTest.after");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldContinueSpanWhenKeyIsUsedOnSpanTagWhenAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			Mono<String> mono = this.testBean.testMethod10_v2("test");

			then(this.reporter.getSpans()).isEmpty();

			mono.block();
		}
		finally {
			span.finish();
		}

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("foo");
			then(spans.get(0).tags()).containsEntry("customTestTag10", "test");
			then(spans.get(0).annotations().stream().map(Annotation::value)
					.collect(Collectors.toList())).contains("customTest.before",
							"customTest.after");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldContinueSpanWithLogWhenAnnotationOnClassMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			// tag::continue_span_execution[]
			Mono<String> mono = this.testBean.testMethod11("test");
			// end::continue_span_execution[]
			then(this.reporter.getSpans()).isEmpty();

			mono.block();
		}
		finally {
			span.finish();
		}

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("foo");
			then(spans.get(0).tags()).containsEntry("class", "TestBean")
					.containsEntry("method", "testMethod11")
					.containsEntry("customTestTag11", "test");
			then(spans.get(0).annotations().stream().map(Annotation::value)
					.collect(Collectors.toList())).contains("customTest.before",
							"customTest.after");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldAddErrorTagWhenExceptionOccurredInNewSpan() {
		try {
			Mono<String> mono = this.testBean.testMethod12("test");

			then(this.reporter.getSpans()).isEmpty();

			mono.block();
		}
		catch (RuntimeException ignored) {
		}

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("test-method12");
			then(spans.get(0).tags()).containsEntry("testTag12", "test")
					.containsEntry("error", "test exception 12");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldAddErrorTagWhenExceptionOccurredInContinueSpan() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			// tag::continue_span_execution[]
			Mono<String> mono = this.testBean.testMethod13();

			then(this.reporter.getSpans()).isEmpty();

			mono.block();
			// end::continue_span_execution[]
		}
		catch (RuntimeException ignored) {
		}
		finally {
			span.finish();
		}

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("foo");
			then(spans.get(0).tags()).containsEntry("error", "test exception 13");
			then(spans.get(0).annotations().stream().map(Annotation::value)
					.collect(Collectors.toList())).contains("testMethod13.before",
							"testMethod13.afterFailure", "testMethod13.after");
			then(spans.get(0).duration()).isNotZero();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldNotCreateSpanWhenNotAnnotated() {
		Mono<String> mono = this.testBean.testMethod7();
		mono.block();

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
			then(spans).isEmpty();
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromTraceContext() {
		Mono<Long> mono = this.testBean.newSpanInTraceContext();

		then(this.reporter.getSpans()).isEmpty();

		Long newSpanId = mono.block();

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("span-in-trace-context");
			then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromTraceContextOuter() {
		Mono<Pair<Pair<Long, Long>, Long>> mono = this.testBeanOuter
				.outerNewSpanInTraceContext();

		then(this.reporter.getSpans()).isEmpty();

		Pair<Pair<Long, Long>, Long> pair = mono.block();
		Long outerSpanIdBefore = pair.getFirst().getFirst();
		Long outerSpanIdAfter = pair.getFirst().getSecond();
		Long innerSpanId = pair.getSecond();

		then(outerSpanIdBefore).isEqualTo(outerSpanIdAfter).isNotEqualTo(innerSpanId);

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(2);
			then(spans.get(0).name()).isEqualTo("outer-span-in-trace-context");
			then(spans.get(0).id()).isEqualTo(toHexString(outerSpanIdBefore));
			then(spans.get(1).name()).isEqualTo("span-in-trace-context");
			then(spans.get(1).id()).isEqualTo(toHexString(innerSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromSubscriberContext() {
		Mono<Long> mono = this.testBean.newSpanInSubscriberContext();

		then(this.reporter.getSpans()).isEmpty();

		Long newSpanId = mono.block();

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).name()).isEqualTo("span-in-subscriber-context");
			then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromSubscriberContextOuter() {
		Mono<Pair<Pair<Long, Long>, Long>> mono = this.testBeanOuter
				.outerNewSpanInSubscriberContext();

		then(this.reporter.getSpans()).isEmpty();

		Pair<Pair<Long, Long>, Long> pair = mono.block();
		Long outerSpanIdBefore = pair.getFirst().getFirst();
		Long outerSpanIdAfter = pair.getFirst().getSecond();
		Long innerSpanId = pair.getSecond();

		then(outerSpanIdBefore).isEqualTo(outerSpanIdAfter).isNotEqualTo(innerSpanId);

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).hasSize(2);
			then(spans.get(0).name()).isEqualTo("outer-span-in-subscriber-context");
			then(spans.get(0).id()).isEqualTo(toHexString(outerSpanIdBefore));
			then(spans.get(1).name()).isEqualTo("span-in-subscriber-context");
			then(spans.get(1).id()).isEqualTo(toHexString(innerSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	protected interface TestBeanInterface {

		// tag::annotated_method[]
		@NewSpan
		Mono<String> testMethod();
		// end::annotated_method[]

		Mono<String> testMethod2();

		@NewSpan(name = "interfaceCustomNameOnTestMethod3")
		Mono<String> testMethod3();

		// tag::custom_name_on_annotated_method[]
		@NewSpan("customNameOnTestMethod4")
		Mono<String> testMethod4();
		// end::custom_name_on_annotated_method[]

		// tag::custom_name_and_tag_on_annotated_method[]
		@NewSpan(name = "customNameOnTestMethod5")
		Mono<String> testMethod5(@SpanTag("testTag") String param);
		// end::custom_name_and_tag_on_annotated_method[]

		Mono<String> testMethod6(String test);

		Mono<String> testMethod7();

		@NewSpan(name = "customNameOnTestMethod8")
		Mono<String> testMethod8(String param);

		@NewSpan(name = "testMethod9")
		Mono<String> testMethod9(String param);

		@ContinueSpan(log = "customTest")
		Mono<String> testMethod10(@SpanTag("testTag10") String param);

		@ContinueSpan(log = "customTest")
		Mono<String> testMethod10_v2(@SpanTag(key = "testTag10") String param);

		// tag::continue_span[]
		@ContinueSpan(log = "testMethod11")
		Mono<String> testMethod11(@SpanTag("testTag11") String param);
		// end::continue_span[]

		@NewSpan
		Mono<String> testMethod12(@SpanTag("testTag12") String param);

		@ContinueSpan(log = "testMethod13")
		Mono<String> testMethod13();

		@NewSpan(name = "spanInTraceContext")
		Mono<Long> newSpanInTraceContext();

		@NewSpan(name = "spanInSubscriberContext")
		Mono<Long> newSpanInSubscriberContext();

	}

	protected static class TestBean implements TestBeanInterface {

		public static final String TEST_STRING = "Test String";

		public static final Mono<String> TEST_MONO = Mono.defer(() -> just(TEST_STRING));

		private final Tracer tracer;

		public TestBean(Tracer tracer) {
			this.tracer = tracer;
		}

		@Override
		public Mono<String> testMethod() {
			return TEST_MONO;
		}

		@NewSpan
		@Override
		public Mono<String> testMethod2() {
			return TEST_MONO;
		}

		// tag::name_on_implementation[]
		@NewSpan(name = "customNameOnTestMethod3")
		@Override
		public Mono<String> testMethod3() {
			return TEST_MONO;
		}
		// end::name_on_implementation[]

		@Override
		public Mono<String> testMethod4() {
			return TEST_MONO;
		}

		@Override
		public Mono<String> testMethod5(String test) {
			return TEST_MONO;
		}

		@NewSpan(name = "customNameOnTestMethod6")
		@Override
		public Mono<String> testMethod6(@SpanTag("testTag6") String test) {
			return TEST_MONO;
		}

		@Override
		public Mono<String> testMethod7() {
			return TEST_MONO;
		}

		@Override
		public Mono<String> testMethod8(String param) {
			return TEST_MONO;
		}

		@NewSpan(name = "customNameOnTestMethod9")
		@Override
		public Mono<String> testMethod9(String param) {
			return TEST_MONO;
		}

		@Override
		public Mono<String> testMethod10(@SpanTag("customTestTag10") String param) {
			return TEST_MONO;
		}

		@Override
		public Mono<String> testMethod10_v2(
				@SpanTag(key = "customTestTag10") String param) {
			return TEST_MONO;
		}

		@ContinueSpan(log = "customTest")
		@Override
		public Mono<String> testMethod11(@SpanTag("customTestTag11") String param) {
			return TEST_MONO;
		}

		@Override
		public Mono<String> testMethod12(String param) {
			return Mono
					.defer(() -> Mono.error(new RuntimeException("test exception 12")));
		}

		@Override
		public Mono<String> testMethod13() {
			return Mono
					.defer(() -> Mono.error(new RuntimeException("test exception 13")));
		}

		@Override
		public Mono<Long> newSpanInTraceContext() {
			return Mono.defer(() -> Mono.just(id(this.tracer)));
		}

		@Override
		public Mono<Long> newSpanInSubscriberContext() {
			return Mono.subscriberContext()
					.flatMap(context -> Mono.just(id(this.tracer)));
		}

	}

	protected static class TestBeanOuter {

		private final Tracer tracer;

		private final TestBeanInterface testBeanInterface;

		public TestBeanOuter(Tracer tracer, TestBeanInterface testBeanInterface) {
			this.tracer = tracer;
			this.testBeanInterface = testBeanInterface;
		}

		@NewSpan(name = "outerSpanInTraceContext")
		public Mono<Pair<Pair<Long, Long>, Long>> outerNewSpanInTraceContext() {
			return Mono.defer(() -> Mono.just(id(this.tracer))
					.zipWith(this.testBeanInterface.newSpanInTraceContext())
					.map(pair -> Pair.of(Pair.of(pair.getT1(), id(this.tracer)),
							pair.getT2())));
		}

		@NewSpan(name = "outerSpanInSubscriberContext")
		public Mono<Pair<Pair<Long, Long>, Long>> outerNewSpanInSubscriberContext() {
			return Mono.subscriberContext()
					.flatMap(context -> Mono.just(id(this.tracer))
							.zipWith(this.testBeanInterface.newSpanInSubscriberContext())
							.map(pair -> Pair.of(Pair.of(pair.getT1(), id(this.tracer)),
									pair.getT2())));
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
		public TestBeanOuter testBeanOuter(Tracer tracer, TestBeanInterface testBean) {
			return new TestBeanOuter(tracer, testBean);
		}

		@Bean
		Reporter<zipkin2.Span> spanReporter() {
			return new ArrayListSpanReporter();
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

}
