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
import org.springframework.cloud.sleuth.assertions.SleuthAssertions;

public class SpanNameUtilTests {

	@Test
	public void should_convert_a_name_in_hyphen_based_notation() throws Exception {
		SleuthAssertions.then(SpanNameUtil.toLowerHyphen("aMethodNameInCamelCaseNotation"))
				.isEqualTo("a-method-name-in-camel-case-notation");
	}
}