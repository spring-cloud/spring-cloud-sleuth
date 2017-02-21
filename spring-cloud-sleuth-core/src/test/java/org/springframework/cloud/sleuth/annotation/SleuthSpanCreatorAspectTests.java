/*
 * Copyright 2013-2016 the original author or authors.
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

import org.assertj.core.api.BDDAssertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.annotation.SleuthSpanCreatorAspectTests.TestConfiguration;
import org.springframework.cloud.sleuth.assertions.ListOfSpans;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@SpringBootTest(classes = TestConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class SleuthSpanCreatorAspectTests {
	
	@Autowired TestBeanInterface testBean;
	@Autowired Tracer tracer;
	@Autowired ArrayListSpanAccumulator accumulator;
	
	@Before
	public void setup() {
		ExceptionUtils.setFail(true);
		this.accumulator.clear();
	}
	
	@Test
	public void shouldCreateSpanWhenAnnotationOnInterfaceMethod() {
		this.testBean.testMethod();
		
		List<Span> spans = new ArrayList<>(this.accumulator.getSpans());
		then(new ListOfSpans(spans)).hasSize(1).hasASpanWithName("testMethod");
		BDDAssertions.then(ExceptionUtils.getLastException()).isNull();
	}
	
	@Test
	public void shouldCreateSpanWhenAnnotationOnClassMethod() {
		this.testBean.testMethod2();

		List<Span> spans = new ArrayList<>(this.accumulator.getSpans());
		then(new ListOfSpans(spans)).hasSize(1).hasASpanWithName("testMethod2");
		then(ExceptionUtils.getLastException()).isNull();
	}
	
	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnClassMethod() {
		this.testBean.testMethod3();

		List<Span> spans = new ArrayList<>(this.accumulator.getSpans());
		then(new ListOfSpans(spans)).hasSize(1).hasASpanWithName("customNameOnTestMethod3");
		then(ExceptionUtils.getLastException()).isNull();
	}
	
	@Test
	public void shouldCreateSpanWithCustomNameWhenAnnotationOnInterfaceMethod() {
		this.testBean.testMethod4();

		List<Span> spans = new ArrayList<>(this.accumulator.getSpans());
		then(new ListOfSpans(spans)).hasSize(1).hasASpanWithName("customNameOnTestMethod4");
		then(ExceptionUtils.getLastException()).isNull();
	}
	
	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnInterfaceMethod() {
		// tag::execution[]
		this.testBean.testMethod5("test");
		// end::execution[]

		List<Span> spans = new ArrayList<>(this.accumulator.getSpans());
		then(new ListOfSpans(spans)).hasSize(1)
				.hasASpanWithName("customNameOnTestMethod5")
				.hasASpanWithTagEqualTo("testTag", "test");
		then(ExceptionUtils.getLastException()).isNull();
	}
	
	@Test
	public void shouldCreateSpanWithTagWhenAnnotationOnClassMethod() {
		this.testBean.testMethod6("test");

		List<Span> spans = new ArrayList<>(this.accumulator.getSpans());
		then(new ListOfSpans(spans)).hasSize(1)
				.hasASpanWithName("customNameOnTestMethod6")
				.hasASpanWithTagEqualTo("testTag6", "test");
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnInterfaceMethod() {
		this.testBean.testMethod8("test");

		List<Span> spans = new ArrayList<>(this.accumulator.getSpans());
		then(new ListOfSpans(spans)).hasSize(1)
				.hasASpanWithName("customNameOnTestMethod8")
				.hasASpanWithLogEqualTo("test.start")
				.hasASpanWithLogEqualTo("test.end");
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void shouldCreateSpanWithLogWhenAnnotationOnClassMethod() {
		// tag::log_execution[]
		this.testBean.testMethod9("test");
		// end::log_execution[]

		List<Span> spans = new ArrayList<>(this.accumulator.getSpans());
		then(new ListOfSpans(spans)).hasSize(1)
				.hasASpanWithName("customNameOnTestMethod9")
				.hasASpanWithLogEqualTo("customTest.start")
				.hasASpanWithLogEqualTo("customTest.end");
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void shouldNotCreateSpanWhenNotAnnotated() {
		this.testBean.testMethod7();

		List<Span> spans = new ArrayList<>(this.accumulator.getSpans());
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

		@NewSpan(name = "customNameOnTestMethod8", log = "test")
		void testMethod8(String param);

		// tag::span_log[]
		@NewSpan(name = "testMethod9", log = "test")
		void testMethod9(String param);
		// end::span_log[]
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

		@NewSpan(name = "customNameOnTestMethod9", log = "customTest")
		@Override
		public void testMethod9(String param) {

		}
	}
	
	@Configuration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		@Bean
		public TestBeanInterface testBean() {
			return new TestBean();
		}

		@Bean SpanReporter spanReporter() {
			return new ArrayListSpanAccumulator();
		}

		@Bean AlwaysSampler alwaysSampler() {
			return new AlwaysSampler();
		}
	}
}
