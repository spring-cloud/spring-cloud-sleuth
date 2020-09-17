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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import brave.sampler.Sampler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(classes = NullSpanTagAnnotationHandlerTests.TestConfiguration.class)

public class NullSpanTagAnnotationHandlerTests {

	@Autowired
	BeanFactory beanFactory;

	@Autowired
	TagValueResolver tagValueResolver;

	SpanTagAnnotationHandler handler;

	@BeforeEach
	public void setup() {
		this.handler = new SpanTagAnnotationHandler(this.beanFactory);
	}

	@Test
	public void shouldUseEmptyStringWheCustomTagValueResolverReturnsNull()
			throws NoSuchMethodException, SecurityException {
		Method method = AnnotationMockClass.class.getMethod("getAnnotationForTagValueResolver", String.class);
		Annotation annotation = method.getParameterAnnotations()[0][0];
		if (annotation instanceof SpanTag) {
			String resolvedValue = this.handler.resolveTagValue((SpanTag) annotation, "test");
			assertThat(resolvedValue).isEqualTo("");
		}
		else {
			fail("Annotation was not SleuthSpanTag");
		}
	}

	@Test
	public void shouldUseEmptyStringWhenTagValueExpressionReturnNull() throws NoSuchMethodException, SecurityException {
		Method method = AnnotationMockClass.class.getMethod("getAnnotationForTagValueExpression", String.class);
		Annotation annotation = method.getParameterAnnotations()[0][0];
		if (annotation instanceof SpanTag) {
			String resolvedValue = this.handler.resolveTagValue((SpanTag) annotation, "test");

			assertThat(resolvedValue).isEqualTo("");
		}
		else {
			fail("Annotation was not SleuthSpanTag");
		}
	}

	@Test
	public void shouldUseEmptyStringWhenArgumentIsNull() throws NoSuchMethodException, SecurityException {
		Method method = AnnotationMockClass.class.getMethod("getAnnotationForArgumentToString", Long.class);
		Annotation annotation = method.getParameterAnnotations()[0][0];
		if (annotation instanceof SpanTag) {
			String resolvedValue = this.handler.resolveTagValue((SpanTag) annotation, null);
			assertThat(resolvedValue).isEqualTo("");
		}
		else {
			fail("Annotation was not SleuthSpanTag");
		}
	}

	@Configuration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		@Bean
		public TagValueResolver tagValueResolver() {
			return parameter -> null;
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

	protected class AnnotationMockClass {

		@NewSpan
		public void getAnnotationForTagValueResolver(
				@SpanTag(key = "test", resolver = TagValueResolver.class) String test) {
		}

		@NewSpan
		public void getAnnotationForTagValueExpression(@SpanTag(key = "test", expression = "null") String test) {
		}

		@NewSpan
		public void getAnnotationForArgumentToString(@SpanTag("test") Long param) {
		}

	}

}
