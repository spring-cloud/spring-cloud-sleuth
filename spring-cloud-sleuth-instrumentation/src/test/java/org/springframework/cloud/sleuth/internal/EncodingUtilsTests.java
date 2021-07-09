/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.internal;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

class EncodingUtilsTests {

	@Test
	void should_convert_back_and_forth_with_64bits() {
		long[] fromString = EncodingUtils.fromString("7c6239a5ad0a4287");
		BDDAssertions.then(fromString).hasSize(1);

		String fromLong = EncodingUtils.fromLong(fromString[0]);

		BDDAssertions.then(fromLong).isEqualTo("7c6239a5ad0a4287");
	}

	@Test
	void should_convert_back_and_forth_with_128bits() {
		long[] fromString = EncodingUtils.fromString("596e1787feb110407c6239a5ad0a4287");
		BDDAssertions.then(fromString).hasSize(2);
		String fromLong = EncodingUtils.fromLongs(fromString[0], fromString[1]);

		BDDAssertions.then(fromLong).isEqualTo("596e1787feb110407c6239a5ad0a4287");
	}

}
