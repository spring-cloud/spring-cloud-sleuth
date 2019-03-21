/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth;

import java.lang.reflect.Method;

import org.junit.Test;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class DefaultSpanNamerTests {

	DefaultSpanNamer defaultSpanNamer = new DefaultSpanNamer();

	@Test
	public void should_return_value_of_span_name_from_annotation() throws Exception {
		then(this.defaultSpanNamer.name(new ClassWithAnnotation(), "default")).isEqualTo("somevalue");
	}

	@Test
	public void should_return_value_of_span_name_from_to_string_if_annotation_is_missing() throws Exception {
		then(this.defaultSpanNamer.name(fromAnonymousClassWithCustomToString(), "default")).isEqualTo("some-other-value");
	}

	@Test
	public void should_return_default_value_if_tostring_wasnt_overridden() throws Exception {
		then(this.defaultSpanNamer.name(new ClassWithoutToString(), "default")).isEqualTo("default");
	}

	@Test
	public void should_return_value_of_span_name_from_annotation_on_method() throws Exception {
		Method method = ReflectionUtils.findMethod(ClassWithAnnotatedMethod.class, "method");
		then(this.defaultSpanNamer.name(method, "default")).isEqualTo("foo");
	}

	@Test
	public void should_return_default_value_of_span_name_from_annotation_on_method() throws Exception {
		Method method = ReflectionUtils.findMethod(ClassWithNonAnnotatedMethod.class, "method");
		then(this.defaultSpanNamer.name(method, "default")).isEqualTo("default");
	}

	@SpanName("somevalue")
	static class ClassWithAnnotation {}

	private Runnable fromAnonymousClassWithCustomToString() {
		return new Runnable() {
			@Override
			public void run() {

			}

			@Override
			public String toString() {
				return "some-other-value";
			}
		};
	}

	static class ClassWithoutToString {}

	static class ClassWithAnnotatedMethod {
		@SpanName("foo")
		void method() {}
	}

	static class ClassWithNonAnnotatedMethod {
		void method() {}
	}
}