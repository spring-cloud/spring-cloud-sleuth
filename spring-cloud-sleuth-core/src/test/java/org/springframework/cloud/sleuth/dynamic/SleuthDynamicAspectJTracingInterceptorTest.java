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

package org.springframework.cloud.sleuth.dynamic;

import brave.sampler.Sampler;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.Span;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Taras Danylchuk
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
		"spring.sleuth.dynamic.tracing.traceParameters=true",
		"spring.sleuth.dynamic.tracing.expression="
				+ "execution(* org.springframework.cloud.sleuth.dynamic.TestClass.testMethodA(..))"
				+ "|| execution(* org.springframework.cloud.sleuth.dynamic.TestClass.testMethodC(..))" },
		classes = SleuthDynamicAspectJTracingInterceptorTest.TestConfiguration.class)
public class SleuthDynamicAspectJTracingInterceptorTest {

	@Autowired
	private TestClass testClass;

	@Autowired
	private ArrayListSpanReporter arrayListSpanReporter;

	@Test
	public void shouldTraceClassViaDynamicAspectJTracing() {
		String parameterA = "parameter A";
		int intParameterA = 125;
		testClass.testMethodA(parameterA, intParameterA);
		testClass.testMethodB("parameterB", 126); // not traced
		testClass.testMethodC();

		assertThat(arrayListSpanReporter.getSpans()).hasSize(2);

		Span testMethodASpan = arrayListSpanReporter.getSpans().get(0);
		assertThat(testMethodASpan.name()).isEqualTo("test-class::test-method-a");
		assertThat(testMethodASpan.tags()).hasSize(4);
		assertThat(testMethodASpan.tags()).containsEntry("class", "TestClass");
		assertThat(testMethodASpan.tags()).containsEntry("method", "testMethodA");
		assertThat(testMethodASpan.tags()).containsEntry("method_parameter_0",
				parameterA);
		assertThat(testMethodASpan.tags()).containsEntry("method_parameter_1",
				String.valueOf(intParameterA));

		Span testMethodCSpan = arrayListSpanReporter.getSpans().get(1);
		assertThat(testMethodCSpan.name()).isEqualTo("test-class::test-method-c");
		assertThat(testMethodCSpan.tags()).hasSize(2);
	}

	@Configuration
	@EnableAutoConfiguration
	public static class TestConfiguration {

		@Bean
		public ArrayListSpanReporter arrayListSpanReporter() {
			return new ArrayListSpanReporter();
		}

		@Bean
		public Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		public TestClass testClass() {
			return new TestClass();
		}

	}

}
