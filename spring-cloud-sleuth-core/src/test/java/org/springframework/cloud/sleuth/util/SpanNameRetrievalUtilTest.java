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

package org.springframework.cloud.sleuth.util;

import org.junit.Test;
import org.springframework.cloud.sleuth.SpanName;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class SpanNameRetrievalUtilTest {

	@Test
	public void should_return_value_of_span_name_from_annotation() throws Exception {
		then(SpanNameRetrievalUtil.getSpanName(new ClassWithAnnotation())).isEqualTo("somevalue");
	}

	@Test
	public void should_return_value_of_span_name_from_to_string_if_annotation_is_missing() throws Exception {
		then(SpanNameRetrievalUtil.getSpanName(fromAnonymousClassWithCustomToString())).isEqualTo("some-other-value");
	}

	@SpanName("somevalue")
	static class ClassWithAnnotation {

	}

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
}