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

package org.springframework.cloud.sleuth.otel.instrument.async;

import org.assertj.core.api.BDDAssertions;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.otel.OtelTestTracing;
import org.springframework.cloud.sleuth.test.TestTracingAware;

public class TraceRunnableTests extends org.springframework.cloud.sleuth.instrument.async.TraceRunnableTests {

	OtelTestTracing testTracing;

	@Override
	public TestTracingAware tracerTest() {
		if (this.testTracing == null) {
			this.testTracing = new OtelTestTracing();
		}
		return this.testTracing;
	}

	@Override
	protected void assertThatThereIsNoParentId(Span secondSpan) {
		BDDAssertions.then(secondSpan.context().parentId()).as("saved span as remnant of first span")
				.isEqualTo(io.opentelemetry.api.trace.Span.getInvalid().getSpanContext().getSpanIdAsHexString());
	}

}
