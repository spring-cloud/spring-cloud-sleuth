/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import zipkin2.Annotation;
import zipkin2.reporter.Reporter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(classes = SleuthSpanCreatorAspectTests.TestConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class SleuthSpanCreatorAspectTests {
	
	@Autowired TestBeanInterface testBean;
	@Autowired Tracer tracer;
	@Autowired ArrayListSpanReporter reporter;
	
	@Before
	public void setup() {
		this.reporter.clear();
	}
	
	@Test
	public void shouldCreateSpanWhenAnnotationOnInterfaceMethod() {
		this.testBean.testMethod();

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("test-method");
		then(spans.get(0).duration()).isNotZero();
	}
	
	@Test
	public void shouldCreateSpanWhenAnnotationOnClassMethod() {
		this.testBean.testMethod2();

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("test-method2");
		then(spans.get(0).duration()).isNotZero();
	}
	
	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnClassMethod() {
		this.testBean.testMethod3();

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method3");
		then(spans.get(0).duration()).isNotZero();
	}
	
	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnInterfaceMethod() {
		this.testBean.testMethod4();

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method4");
		then(spans.get(0).duration()).isNotZero();
	}
	
	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnInterfaceMethod() {
		// tag::execution[]
		this.testBean.testMethod5("test");
		// end::execution[]

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method5");
		then(spans.get(0).tags()).containsEntry("testTag", "test");
		then(spans.get(0).duration()).isNotZero();
	}
	
	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnClassMethod() {
		this.testBean.testMethod6("test");

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method6");
		then(spans.get(0).tags()).containsEntry("testTag6", "test");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnInterfaceMethod() {
		this.testBean.testMethod8("test");

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method8");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnClassMethod() {
		this.testBean.testMethod9("test");

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method9");
		then(spans.get(0).tags())
				.containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod9");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldContinueSpanWithLogWhenAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.testBean.testMethod10("test");
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("foo");
		then(spans.get(0).tags())
				.containsEntry("customTestTag10", "test");
		then(spans.get(0).annotations()
				.stream().map(Annotation::value).collect(Collectors.toList()))
				.contains("customTest.before", "customTest.after");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldContinueSpanWhenKeyIsUsedOnSpanTagWhenAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.testBean.testMethod10_v2("test");
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("foo");
		then(spans.get(0).tags())
				.containsEntry("customTestTag10", "test");
		then(spans.get(0).annotations()
				.stream().map(Annotation::value).collect(Collectors.toList()))
				.contains("customTest.before", "customTest.after");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldContinueSpanWithLogWhenAnnotationOnClassMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			// tag::continue_span_execution[]
			this.testBean.testMethod11("test");
			// end::continue_span_execution[]
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("foo");
		then(spans.get(0).tags())
				.containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod11")
				.containsEntry("customTestTag11", "test");
		then(spans.get(0).annotations()
				.stream().map(Annotation::value).collect(Collectors.toList()))
				.contains("customTest.before", "customTest.after");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldAddErrorTagWhenExceptionOccurredInNewSpan() {
		try {
			this.testBean.testMethod12("test");
		} catch (RuntimeException ignored) {
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("test-method12");
		then(spans.get(0).tags())
				.containsEntry("testTag12", "test")
				.containsEntry("error", "test exception 12");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldAddErrorTagWhenExceptionOccurredInContinueSpan() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			// tag::continue_span_execution[]
			this.testBean.testMethod13();
			// end::continue_span_execution[]
		} catch (RuntimeException ignored) {
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("foo");
		then(spans.get(0).tags())
				.containsEntry("error", "test exception 13");
		then(spans.get(0).annotations()
				.stream().map(Annotation::value).collect(Collectors.toList()))
				.contains("testMethod13.before", "testMethod13.afterFailure",
						"testMethod13.after");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldAddTagForReturnValueWhenAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.testBean.testMethod14();
		} catch (RuntimeException ignored) {
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method14");
		then(spans.get(0).tags())
				.containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod14")
				.containsEntry("testTag", "TestValue");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldAddTagForReturnValueWhenAnnotationOnClassMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.testBean.testMethod15();
		} catch (RuntimeException ignored) {
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method15");
		then(spans.get(0).tags())
				.containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod15")
				.containsEntry("testTag", "TestValue");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldNotAddTagForReturnValueWhenMethodIsVoidAndAnnotationOnInterfaceMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.testBean.testMethod16();
		} catch (RuntimeException ignored) {
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method16");
		then(spans.get(0).tags())
				.containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod16")
				.doesNotContainKey("testTag");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldNotAddTagForReturnValueWhenMethodIsVoidAndAnnotationOnClassMethod() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.testBean.testMethod17();
		} catch (RuntimeException ignored) {
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method17");
		then(spans.get(0).tags())
				.containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod17")
				.doesNotContainKey("testTag");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldNotAddTagForReturnValueWhenMethodThrows() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.testBean.testMethod18();
		} catch (RuntimeException ignored) {
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method18");
		then(spans.get(0).tags())
				.containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod18")
				.containsEntry("error", "test exception 18")
				.doesNotContainKey("testTag");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldAddEmptyTagForReturnValueWhenMethodReturnsNullAndNeitherResolverNorExpressionAreSpecified() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.testBean.testMethod19();
		} catch (RuntimeException ignored) {
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method19");
		then(spans.get(0).tags())
				.containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod19")
				.containsEntry("testTag", "");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldResolveEmptyTagValueForReturnValueWhenMethodReturnsNullAndResolverIsSpecified() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.testBean.testMethod20();
		} catch (RuntimeException ignored) {
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method20");
		then(spans.get(0).tags())
				.containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod20")
				.containsEntry("testTag", "");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldResolveEmptyTagValueForReturnValueWhenMethodReturnsNullAndExpressionIsSpecified() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.testBean.testMethod21();
		} catch (RuntimeException ignored) {
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method21");
		then(spans.get(0).tags())
				.containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod21")
				.containsEntry("testTag", "");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldResolveTagValueForReturnValueWhenResolverIsSpecified() {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.testBean.testMethod22();
		} catch (RuntimeException ignored) {
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans.get(0).name()).isEqualTo("custom-name-on-test-method22");
		then(spans.get(0).tags())
				.containsEntry("class", "TestBean")
				.containsEntry("method", "testMethod22")
				.containsEntry("testTag", "ciao");
		then(spans.get(0).duration()).isNotZero();
	}

	@Test
	public void shouldNotCreateSpanWhenNotAnnotated() {
		this.testBean.testMethod7();

		List<zipkin2.Span> spans = new ArrayList<>(this.reporter.getSpans());
		then(spans).isEmpty();
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
		void testMethod10(@SpanTag(value = "testTag10") String param);

		@ContinueSpan(log = "customTest")
		void testMethod10_v2(@SpanTag(key = "testTag10") String param);

		// tag::continue_span[]
		@ContinueSpan(log = "testMethod11")
		void testMethod11(@SpanTag("testTag11") String param);
		// end::continue_span[]

		@NewSpan
		void testMethod12(@SpanTag("testTag12") String param);

		@ContinueSpan(log = "testMethod13")
		void testMethod13();

		@NewSpan(name = "customNameOnTestMethod14")
		@SpanTagFromReturnValue("testTag")
		String testMethod14();

		@NewSpan(name = "customNameOnTestMethod15")
		String testMethod15();

		@NewSpan(name = "customNameOnTestMethod16")
		@SpanTagFromReturnValue("testTag")
		void testMethod16();

		@NewSpan(name = "customNameOnTestMethod17")
		void testMethod17();

		@NewSpan(name = "customNameOnTestMethod18")
		@SpanTagFromReturnValue("testTag")
		String testMethod18();

		@NewSpan(name = "customNameOnTestMethod19")
		@SpanTagFromReturnValue("testTag")
		String testMethod19();

		@NewSpan(name = "customNameOnTestMethod20")
		@SpanTagFromReturnValue(value = "testTag", resolver = TagValueResolver.class)
		String testMethod20();

		@NewSpan(name = "customNameOnTestMethod21")
		@SpanTagFromReturnValue(value = "testTag", expression = "#{== null}")
		String testMethod21();

		@NewSpan(name = "customNameOnTestMethod22")
		@SpanTagFromReturnValue(value = "testTag", resolver = TagValueResolver.class)
		String testMethod22();

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
		public void testMethod10(@SpanTag(value = "customTestTag10") String param) {

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

		@Override
		public String testMethod14() {
			return "TestValue";
		}

		@SpanTagFromReturnValue("testTag")
		@Override
		public String testMethod15() {
			return "TestValue";
		}

		@NewSpan(name = "customNameOnTestMethod16")
		@Override
		public void testMethod16() {}

		@NewSpan(name = "customNameOnTestMethod17")
		@SpanTagFromReturnValue("testTag")
		@Override
		public void testMethod17() {}

		@Override
		public String testMethod18() {
			throw new RuntimeException("test exception 18");
		}

		@Override
		public String testMethod19() {
			return null;
		}

		@Override
		public String testMethod20() {
			return null;
		}

		@Override
		public String testMethod21() {
			return null;
		}

		@Override
		public String testMethod22() {
			return "";
		}
	}

	@Configuration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		@Bean
		public TestBeanInterface testBean() {
			return new TestBean();
		}

		@Bean Reporter<zipkin2.Span> spanReporter() {
			return new ArrayListSpanReporter();
		}

		@Bean Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean TagValueResolver ciaoResolver() {
			return parameter -> "ciao";
		}
	}
}
