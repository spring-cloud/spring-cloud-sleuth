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
import java.util.stream.Collectors;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = SleuthSpanCreatorAspectTests.TestConfiguration.class)
public abstract class SleuthSpanCreatorAspectTests {

	@Autowired
	TestBeanInterface testBean;

	@Autowired
	Tracer tracer;

	@Autowired
	TestSpanHandler spans;

	@BeforeEach
	public void setup() {
		this.spans.clear();
	}

	@Test
	public void shouldCreateSpanWhenAnnotationOnInterfaceMethod() {
		this.testBean.testMethod();

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("test-method");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldCreateSpanWhenAnnotationOnClassMethod() {
		this.testBean.testMethod2();

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("test-method2");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnClassMethod() {
		this.testBean.testMethod3();

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method3");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnInterfaceMethod() {
		this.testBean.testMethod4();

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method4");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnInterfaceMethod() {
		// tag::execution[]
		this.testBean.testMethod5("test");
		// end::execution[]

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method5");
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("testTag", "test");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnClassMethod() {
		this.testBean.testMethod6("test");

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method6");
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("testTag6", "test");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnInterfaceMethod() {
		this.testBean.testMethod8("test");

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method8");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnClassMethod() {
		this.testBean.testMethod9("test");

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("custom-name-on-test-method9");
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("class", "TestBean").containsEntry("method",
				"testMethod9");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldContinueSpanWithLogWhenAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			this.testBean.testMethod10("test");
		}
		finally {
			span.end();
		}

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("foo");
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("customTestTag10", "test");
		BDDAssertions.then(this.spans.get(0).getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
				.contains("customTest.before", "customTest.after");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldStartAndCloseSpanOnContinueSpanIfSpanNotSet() {
		this.testBean.testMethod10("test");

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("test-method10");
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("customTestTag10", "test");
		BDDAssertions.then(this.spans.get(0).getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
				.contains("customTest.before", "customTest.after");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldContinueSpanWhenKeyIsUsedOnSpanTagWhenAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			this.testBean.testMethod10_v2("test");
		}
		finally {
			span.end();
		}

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("foo");
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("customTestTag10", "test");
		BDDAssertions.then(this.spans.get(0).getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
				.contains("customTest.before", "customTest.after");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldContinueSpanWithLogWhenAnnotationOnClassMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			// tag::continue_span_execution[]
			this.testBean.testMethod11("test");
			// end::continue_span_execution[]
		}
		finally {
			span.end();
		}

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("foo");
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod11").containsEntry("customTestTag11", "test");
		BDDAssertions.then(this.spans.get(0).getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
				.contains("customTest.before", "customTest.after");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldAddErrorTagWhenExceptionOccurredInNewSpan() {
		try {
			this.testBean.testMethod12("test");
		}
		catch (RuntimeException ignored) {
		}

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("test-method12");
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("testTag12", "test");
		BDDAssertions.then(this.spans.get(0).getError()).hasMessageContaining("test exception 12");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldAddErrorTagWhenExceptionOccurredInContinueSpan() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			// tag::continue_span_execution[]
			this.testBean.testMethod13();
			// end::continue_span_execution[]
		}
		catch (RuntimeException ignored) {
		}
		finally {
			span.end();
		}

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("foo");
		BDDAssertions.then(this.spans.get(0).getError()).hasMessageContaining("test exception 13");
		BDDAssertions.then(this.spans.get(0).getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
				.contains("testMethod13.before", "testMethod13.afterFailure", "testMethod13.after");
		BDDAssertions.then(this.spans.get(0).getEndTimestamp()).isNotZero();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldNotCreateSpanWhenNotAnnotated() {
		this.testBean.testMethod7();

		BDDAssertions.then(this.spans).isEmpty();
		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	protected interface TestBeanInterface {

		// tag::annotated_method[]
		@NewSpan
		void testMethod();
		// end::annotated_method[]

		void testMethod2();

		@NewSpan(name = "interfaceCustomNameOnTestMethod3")
		void testMethod3();

		// tag::custom_name_on_annotated_method[]
		@NewSpan("customNameOnTestMethod4")
		void testMethod4();
		// end::custom_name_on_annotated_method[]

		// tag::custom_name_and_tag_on_annotated_method[]
		@NewSpan(name = "customNameOnTestMethod5")
		void testMethod5(@SpanTag("testTag") String param);
		// end::custom_name_and_tag_on_annotated_method[]

		void testMethod6(String test);

		void testMethod7();

		@NewSpan(name = "customNameOnTestMethod8")
		void testMethod8(String param);

		@NewSpan(name = "testMethod9")
		void testMethod9(String param);

		@ContinueSpan(log = "customTest")
		void testMethod10(@SpanTag("testTag10") String param);

		@ContinueSpan(log = "customTest")
		void testMethod10_v2(@SpanTag("testTag10") String param);

		// tag::continue_span[]
		@ContinueSpan(log = "testMethod11")
		void testMethod11(@SpanTag("testTag11") String param);
		// end::continue_span[]

		@NewSpan
		void testMethod12(@SpanTag("testTag12") String param);

		@ContinueSpan(log = "testMethod13")
		void testMethod13();

	}

	protected static class TestBean implements TestBeanInterface {

		@Override
		public void testMethod() {
		}

		@NewSpan
		@Override
		public void testMethod2() {
		}

		// tag::name_on_implementation[]
		@NewSpan(name = "customNameOnTestMethod3")
		@Override
		public void testMethod3() {
		}
		// end::name_on_implementation[]

		@Override
		public void testMethod4() {
		}

		@Override
		public void testMethod5(String test) {
		}

		@NewSpan(name = "customNameOnTestMethod6")
		@Override
		public void testMethod6(@SpanTag("testTag6") String test) {

		}

		@Override
		public void testMethod7() {
		}

		@Override
		public void testMethod8(String param) {

		}

		@NewSpan(name = "customNameOnTestMethod9")
		@Override
		public void testMethod9(String param) {

		}

		@Override
		public void testMethod10(@SpanTag("customTestTag10") String param) {

		}

		@Override
		public void testMethod10_v2(@SpanTag(key = "customTestTag10") String param) {

		}

		@ContinueSpan(log = "customTest")
		@Override
		public void testMethod11(@SpanTag("customTestTag11") String param) {

		}

		@Override
		public void testMethod12(String param) {
			throw new RuntimeException("test exception 12");
		}

		@Override
		public void testMethod13() {
			throw new RuntimeException("test exception 13");
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	public static class TestConfiguration {

		@Bean
		TestBeanInterface testBean() {
			return new TestBean();
		}

	}

}
