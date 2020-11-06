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

package org.springframework.cloud.sleuth.otel;

import org.assertj.core.api.BDDAssertions;

import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestTracingAssertions;

public class OtelTestTracingAssertions implements TestTracingAssertions {

	@Override
	public void assertThatNoParentPresent(FinishedSpan finishedSpan) {
		BDDAssertions.then(Long.valueOf(finishedSpan.getParentId())).isEqualTo(0L);
	}

	@Override
	public String or128Bit(String id) {
		return "0000000000000000" + id;
	}

}
