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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import brave.sampler.Sampler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(classes = SpanTagAnnotationHandlerTests.TestConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class SpanTagAnnotationHandlerTests {

	@Autowired BeanFactory beanFactory;
	@Autowired TagValueResolver tagValueResolver;
	SpanTagAnnotationHandler handler;

	@Before
	public void setup() {
		this.handler = new SpanTagAnnotationHandler(this.beanFactory);
	}

	@Test
	public void shouldUseCustomTagValueResolver() throws NoSuchMethodException, SecurityException {
		Method method = AnnotationMockClass.class.getMethod("getAnnotationForTagValueResolver", String.class);
		Annotation annotation = method.getParameterAnnotations()[0][0];
		if (annotation instanceof SpanTag) {
			String resolvedValue = handler.resolveTagValue((SpanTag) annotation, "test");
			assertThat(resolvedValue).isEqualTo("Value from myCustomTagValueResolver");
		} else {
			fail("Annotation was not SleuthSpanTag");
		}
	}
	
	@Test
	public void shouldUseTagValueExpression() throws NoSuchMethodException, SecurityException {
		Method method = AnnotationMockClass.class.getMethod("getAnnotationForTagValueExpression", String.class);
		Annotation annotation = method.getParameterAnnotations()[0][0];
		if (annotation instanceof SpanTag) {
			String resolvedValue = handler.resolveTagValue((SpanTag) annotation, "test");
			
			assertThat(resolvedValue).isEqualTo("hello characters");
		} else {
			fail("Annotation was not SleuthSpanTag");
		}
	}

	@Test
	public void shouldReturnArgumentToString() throws NoSuchMethodException, SecurityException {
		Method method = AnnotationMockClass.class.getMethod("getAnnotationForArgumentToString", Long.class);
		Annotation annotation = method.getParameterAnnotations()[0][0];
		if (annotation instanceof SpanTag) {
			String resolvedValue = handler.resolveTagValue((SpanTag) annotation, 15);
			assertThat(resolvedValue).isEqualTo("15");
		} else {
			fail("Annotation was not SleuthSpanTag");
		}
	}

	protected class AnnotationMockClass {

		// tag::resolver_bean[]
		@NewSpan
		public void getAnnotationForTagValueResolver(@SpanTag(key = "test", resolver = TagValueResolver.class) String test) {
		}
		// end::resolver_bean[]

		// tag::spel[]
		@NewSpan
		public void getAnnotationForTagValueExpression(@SpanTag(key = "test", expression = "'hello' + ' characters'") String test) {
		}
		// end::spel[]

		// tag::toString[]
		@NewSpan
		public void getAnnotationForArgumentToString(@SpanTag("test") Long param) {
		}
		// end::toString[]
	}
	
	@Configuration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		// tag::custom_resolver[]
		@Bean(name = "myCustomTagValueResolver")
		public TagValueResolver tagValueResolver() {
			return parameter -> "Value from myCustomTagValueResolver";
		}
		// end::custom_resolver[]

		@Bean Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}
	}

}
