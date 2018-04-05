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

import org.junit.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class SpelTagValueExpressionResolverTests {
	@Test
	public void should_use_spel_to_resolve_a_value() throws Exception {
		SpelTagValueExpressionResolver resolver = new SpelTagValueExpressionResolver();
		MyObject myObject = new MyObject();
		myObject.name = "hello";

		String resolved = resolver.resolve("name + ' world'", myObject);

		then(resolved).isEqualTo("hello world");
	}

	public static class MyObject {
		public String name;
	}

	@Test
	public void should_use_to_string_if_expression_is_not_analyzed_properly() throws Exception {
		SpelTagValueExpressionResolver resolver = new SpelTagValueExpressionResolver();

		String resolved = resolver.resolve("invalid() structure + 1", new Foo());

		then(resolved).isEqualTo("BAR");
	}
}

class Foo {
	@Override public String toString() {
		return "BAR";
	}
}