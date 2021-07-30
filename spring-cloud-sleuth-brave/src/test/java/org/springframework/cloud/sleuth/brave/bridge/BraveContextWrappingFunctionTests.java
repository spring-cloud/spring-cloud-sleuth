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
import reactor.util.context.Context;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;

class BraveContextWrappingFunctionTests {

	BraveContextWrappingFunction function = new BraveContextWrappingFunction();

	@Test
	void should_not_mutate_context_when_no_tracing_information_is_set() {
		Context context = Context.empty();

		then(this.function.apply(context)).isSameAs(context);
	}

	@Test
	void should_not_mutate_context_when_brave_trace_context_is_already_there() {
		Context context = Context.of(Span.class, mock(Span.class), TraceContext.class, mock(TraceContext.class),
				brave.propagation.TraceContext.class, traceContext());

		then(this.function.apply(context)).isSameAs(context);
	}

	@Test
	void should_mutate_context_when_there_is_tracing_info_but_brave_version_is_missing() {
		Context context = Context.of(Span.class, new BraveSpan(mock(brave.Span.class)), TraceContext.class,
				new BraveTraceContext(traceContext()));

		Context mutatedContext = this.function.apply(context);

		then(mutatedContext.hasKey(brave.Span.class)).isTrue();
		then(mutatedContext.hasKey(brave.propagation.TraceContext.class)).isTrue();
	}

	private brave.propagation.TraceContext traceContext() {
		return brave.propagation.TraceContext.newBuilder().spanId(1L).traceId(2L).build();
	}

}
