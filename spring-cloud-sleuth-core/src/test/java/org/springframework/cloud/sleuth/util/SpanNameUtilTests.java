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

package org.springframework.cloud.sleuth.util;

import org.assertj.core.api.BDDAssertions;
import org.junit.Test;

public class SpanNameUtilTests {

	@Test
	public void should_convert_a_name_in_hyphen_based_notation() throws Exception {
		BDDAssertions.then(SpanNameUtil.toLowerHyphen("aMethodNameInCamelCaseNotation"))
				.isEqualTo("a-method-name-in-camel-case-notation");
	}

	@Test
	public void should_convert_a_class_name_in_hyphen_based_notation() throws Exception {
		BDDAssertions.then(SpanNameUtil.toLowerHyphen("MySuperClassName"))
				.isEqualTo("my-super-class-name");
	}

	@Test
	public void should_not_shorten_a_name_that_is_below_max_threshold() throws Exception {
		BDDAssertions.then(SpanNameUtil.shorten("someName"))
				.isEqualTo("someName");
	}

	@Test
	public void should_not_shorten_a_name_that_is_null() throws Exception {
		BDDAssertions.then(SpanNameUtil.shorten(null)).isNull();
	}

	@Test
	public void should_shorten_a_name_that_is_above_max_threshold() throws Exception {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			sb.append("a");
		}
		BDDAssertions.then(SpanNameUtil.shorten(sb.toString()).length())
				.isEqualTo(SpanNameUtil.MAX_NAME_LENGTH);
	}
}