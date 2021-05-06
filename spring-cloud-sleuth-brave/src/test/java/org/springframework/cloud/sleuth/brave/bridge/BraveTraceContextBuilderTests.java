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

package org.springframework.cloud.sleuth.brave.bridge;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.TraceContext;

import static org.assertj.core.api.BDDAssertions.then;

class BraveTraceContextBuilderTests {

	@Test
	void should_set_trace_context_for_64_bit() {
		BraveTraceContextBuilder builder = new BraveTraceContextBuilder();

		TraceContext traceContext = builder.parentId("7c6239a5ad0a4287").spanId("caff89f7f0f229dd")
				.traceId("596e1787feb11040").sampled(true).build();

		then(traceContext.parentId()).isEqualTo("7c6239a5ad0a4287");
		then(traceContext.spanId()).isEqualTo("caff89f7f0f229dd");
		then(traceContext.traceId()).isEqualTo("596e1787feb11040");
		then(traceContext.sampled()).isTrue();
	}

	@Test
	void should_set_trace_context_for_128_bit() {
		BraveTraceContextBuilder builder = new BraveTraceContextBuilder();

		TraceContext traceContext = builder.parentId("00000000000000007c6239a5ad0a4287")
				.spanId("0000000000000000caff89f7f0f229dd").traceId("596e1787feb11040caff89f7f0f229dd").sampled(true)
				.build();

		then(traceContext.parentId()).isEqualTo("7c6239a5ad0a4287");
		then(traceContext.spanId()).isEqualTo("caff89f7f0f229dd");
		then(traceContext.traceId()).isEqualTo("596e1787feb11040caff89f7f0f229dd");
		then(traceContext.sampled()).isTrue();
	}

}
