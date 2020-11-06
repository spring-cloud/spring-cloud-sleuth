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

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.annotation.SleuthSpanCreatorAspectMonoTests.TestBean.TEST_STRING;
import static reactor.core.publisher.Mono.just;

@ContextConfiguration(classes = SleuthSpanCreatorAspectMonoTests.TestConfiguration.class)
public abstract class SleuthSpanCreatorAspectMonoTests {

	@Autowired
	TestBeanInterface testBean;

	@Autowired
	TestBeanOuter testBeanOuter;

	@Autowired
	Tracer tracer;

	@Autowired
	TestSpanHandler spans;

	protected static String id(Tracer tracer) {
		if (tracer.currentSpan() == null) {
			throw new IllegalStateException("Current Span is supposed to have a value!");
		}
		return tracer.currentSpan().context().spanId();
	}

	@BeforeEach
	public void setup() {
		this.spans.clear();
	}

	@Test
	public void shouldCreateSpanWhenAnnotationOnInterfaceMethod() {
		Mono<String> mono = this.testBean.testMethod();

		BDDAssertions.then(this.spans).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("test-method");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWhenAnnotationOnClassMethod() {
		Mono<String> mono = this.testBean.testMethod2();

		BDDAssertions.then(this.spans).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("test-method2");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnClassMethod() {
		Mono<String> mono = this.testBean.testMethod3();

		BDDAssertions.then(this.spans).isEmpty();

		String result = mono.block();

		Awaitility.await().untilAsserted(() -> {
			then(result).isEqualTo(TEST_STRING);
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method3");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnInterfaceMethod() {
		Mono<String> mono = this.testBean.testMethod4();

		BDDAssertions.then(this.spans).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method4");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnInterfaceMethod() {
		// tag::execution[]
		Mono<String> mono = this.testBean.testMethod5("test");

		// end::execution[]
		BDDAssertions.then(this.spans).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method5");
			BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("testTag", "test");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnClassMethod() {
		Mono<String> mono = this.testBean.testMethod6("test");

		BDDAssertions.then(this.spans).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method6");
			BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("testTag6", "test");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnInterfaceMethod() {
		Mono<String> mono = this.testBean.testMethod8("test");

		BDDAssertions.then(this.spans).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method8");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnClassMethod() {
		Mono<String> mono = this.testBean.testMethod9("test");

		BDDAssertions.then(this.spans).isEmpty();

		mono.block();

		Awaitility.await().untilAsserted(() -> {
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
			Mono<String> mono = this.testBean.testMethod10("test");

			BDDAssertions.then(this.spans).isEmpty();

			mono.block();
		}
		finally {
			span.end();
		}

		Awaitility.await().untilAsserted(() -> {
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
	public void shouldStartAndCloseSpanOnContinueSpanIfSpanNotSet() {
		this.testBean.testMethod10("test").block();

		Awaitility.await().untilAsserted(() -> {
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
			Mono<String> mono = this.testBean.testMethod10_v2("test");

			BDDAssertions.then(this.spans).isEmpty();

			mono.block();
		}
		finally {
			span.end();
		}

		Awaitility.await().untilAsserted(() -> {
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
			Mono<String> mono = this.testBean.testMethod11("test");
			// end::continue_span_execution[]
			BDDAssertions.then(this.spans).isEmpty();

			mono.block();
		}
		finally {
			span.end();
		}

		Awaitility.await().untilAsserted(() -> {
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
			Mono<String> mono = this.testBean.testMethod12("test");

			BDDAssertions.then(this.spans).isEmpty();

			mono.block();
		}
		catch (RuntimeException ignored) {
		}

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("test-method12");
			BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("testTag12", "test");
			BDDAssertions.then(this.spans.get(0).getError()).hasMessageContaining("test exception 12");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldAddErrorTagWhenExceptionOccurredInContinueSpan() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			// tag::continue_span_execution[]
			Mono<String> mono = this.testBean.testMethod13();

			BDDAssertions.then(this.spans).isEmpty();

			mono.block();
			// end::continue_span_execution[]
		}
		catch (RuntimeException ignored) {
		}
		finally {
			span.end();
		}

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("foo");
			BDDAssertions.then(this.spans.get(0).getError()).hasMessageContaining("test exception 13");
			BDDAssertions
					.then(this.spans.get(0).getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
					.contains("testMethod13.before", "testMethod13.afterFailure", "testMethod13.after");
			BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldNotCreateSpanWhenNotAnnotated() {
		Mono<String> mono = this.testBean.testMethod7();
		mono.block();

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.spans).isEmpty();
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromTraceContext() {
		Mono<String> mono = this.testBean.newSpanInTraceContext();

		BDDAssertions.then(this.spans).isEmpty();

		String newSpanId = mono.block();

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("span-in-trace-context");
			BDDAssertions.then(this.spans.get(0).getSpanId()).isEqualTo(newSpanId);
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromTraceContextOuter() {
		Mono<Pair<Pair<String, String>, String>> mono = this.testBeanOuter.outerNewSpanInTraceContext();

		BDDAssertions.then(this.spans).isEmpty();

		Pair<Pair<String, String>, String> pair = mono.block();
		String outerSpanIdBefore = pair.getFirst().getFirst();
		String innerSpanId = pair.getSecond();

		then(outerSpanIdBefore).isNotEqualTo(innerSpanId);

		Awaitility.await().untilAsserted(() -> {
			FinishedSpan outerSpan = spans.reportedSpans().stream()
					.filter(span -> span.getName().equals("outer-span-in-trace-context")).findFirst()
					.orElseThrow(() -> new AssertionError("No span with name [outer-span-in-trace-context] found"));
			BDDAssertions.then(outerSpan.getName()).isEqualTo("outer-span-in-trace-context");
			BDDAssertions.then(outerSpan.getSpanId()).isEqualTo(outerSpanIdBefore);
			FinishedSpan innerSpan = spans.reportedSpans().stream()
					.filter(span -> span.getName().equals("span-in-trace-context")).findFirst()
					.orElseThrow(() -> new AssertionError("No span with name [span-in-trace-context] found"));
			BDDAssertions.then(innerSpan.getName()).isEqualTo("span-in-trace-context");
			BDDAssertions.then(innerSpan.getSpanId()).isEqualTo(innerSpanId);
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromSubscriberContext() {
		Mono<String> mono = this.testBean.newSpanInSubscriberContext();

		BDDAssertions.then(this.spans).isEmpty();

		String newSpanId = mono.block();

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.spans).hasSize(1);
			BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("span-in-subscriber-context");
			BDDAssertions.then(this.spans.get(0).getSpanId()).isEqualTo(newSpanId);
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldReturnNewSpanFromSubscriberContextOuter() {
		Mono<Pair<Pair<String, String>, String>> mono = this.testBeanOuter.outerNewSpanInSubscriberContext();

		BDDAssertions.then(this.spans).isEmpty();

		Pair<Pair<String, String>, String> pair = mono.block();
		String outerSpanIdBefore = pair.getFirst().getFirst();
		String innerSpanId = pair.getSecond();

		then(outerSpanIdBefore).isNotEqualTo(innerSpanId);

		Awaitility.await().untilAsserted(() -> {
			FinishedSpan outerSpan = spans.reportedSpans().stream()
					.filter(span -> span.getName().equals("outer-span-in-subscriber-context")).findFirst().orElseThrow(
							() -> new AssertionError("No span with name [outer-span-in-subscriber-context] found"));
			BDDAssertions.then(outerSpan.getName()).isEqualTo("outer-span-in-subscriber-context");
			BDDAssertions.then(outerSpan.getSpanId()).isEqualTo(outerSpanIdBefore);
			FinishedSpan innerSpan = spans.reportedSpans().stream()
					.filter(span -> span.getName().equals("span-in-subscriber-context")).findFirst()
					.orElseThrow(() -> new AssertionError("No span with name [span-in-subscriber-context] found"));
			BDDAssertions.then(innerSpan.getName()).isEqualTo("span-in-subscriber-context");
			BDDAssertions.then(innerSpan.getSpanId()).isEqualTo(innerSpanId);
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
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
		Mono<String> newSpanInTraceContext();

		@NewSpan(name = "spanInSubscriberContext")
		Mono<String> newSpanInSubscriberContext();

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
		public Mono<String> testMethod10_v2(@SpanTag(key = "customTestTag10") String param) {
			return TEST_MONO;
		}

		@ContinueSpan(log = "customTest")
		@Override
		public Mono<String> testMethod11(@SpanTag("customTestTag11") String param) {
			return TEST_MONO;
		}

		@Override
		public Mono<String> testMethod12(String param) {
			return Mono.defer(() -> Mono.error(new RuntimeException("test exception 12")));
		}

		@Override
		public Mono<String> testMethod13() {
			return Mono.defer(() -> Mono.error(new RuntimeException("test exception 13")));
		}

		@Override
		public Mono<String> newSpanInTraceContext() {
			return Mono.defer(() -> Mono.just(id(this.tracer)));
		}

		@Override
		public Mono<String> newSpanInSubscriberContext() {
			return Mono.subscriberContext().flatMap(context -> Mono.just(id(this.tracer)));
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
		public Mono<Pair<Pair<String, String>, String>> outerNewSpanInTraceContext() {
			return Mono.defer(() -> Mono.just(id(this.tracer)).zipWith(this.testBeanInterface.newSpanInTraceContext())
					.map(pair -> Pair.of(Pair.of(pair.getT1(), id(this.tracer)), pair.getT2())));
		}

		@NewSpan(name = "outerSpanInSubscriberContext")
		public Mono<Pair<Pair<String, String>, String>> outerNewSpanInSubscriberContext() {
			return Mono.subscriberContext()
					.flatMap(context -> Mono.just(id(this.tracer))
							.zipWith(this.testBeanInterface.newSpanInSubscriberContext())
							.map(pair -> Pair.of(Pair.of(pair.getT1(), id(this.tracer)), pair.getT2())));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	public static class TestConfiguration {

		@Bean
		TestBeanInterface testBean(Tracer tracer) {
			return new TestBean(tracer);
		}

		@Bean
		TestBeanOuter testBeanOuter(Tracer tracer, TestBeanInterface testBean) {
			return new TestBeanOuter(tracer, testBean);
		}

	}

}

/**
 * Copied from Spring Data
 */
final class Pair<S, T> {

	private final S first;

	private final T second;

	Pair(S first, T second) {
		this.first = first;
		this.second = second;
	}

	public static <S, T> Pair<S, T> of(S first, T second) {
		return new Pair<>(first, second);
	}

	public S getFirst() {
		return first;
	}

	public T getSecond() {
		return second;
	}

	public static <S, T> Collector<Pair<S, T>, ?, Map<S, T>> toMap() {
		return Collectors.toMap(Pair::getFirst, Pair::getSecond);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Pair<?, ?> pair = (Pair<?, ?>) o;
		return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
	}

	@Override
	public int hashCode() {
		return Objects.hash(first, second);
	}

}
