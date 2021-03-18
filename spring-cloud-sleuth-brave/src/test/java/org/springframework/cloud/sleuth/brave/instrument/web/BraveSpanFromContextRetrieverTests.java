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

package org.springframework.cloud.sleuth.brave.instrument.web;

import brave.Span;
import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import org.springframework.cloud.sleuth.brave.bridge.BraveSpan;

import static org.assertj.core.api.BDDAssertions.then;

class BraveSpanFromContextRetrieverTests {

	TestSpanHandler spans = new TestSpanHandler();

	StrictCurrentTraceContext traceContext = StrictCurrentTraceContext.create();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(this.traceContext).sampler(Sampler.ALWAYS_SAMPLE)
			.addSpanHandler(this.spans).build();

	brave.Tracer tracer = this.tracing.tracer();

	BraveSpanFromContextRetriever retriever = new BraveSpanFromContextRetriever(this.traceContext, this.tracer);

	@Test
	void should_return_null_when_no_brave_specific_entries_are_present_in_context() {
		then(retriever.findSpan(Context.empty())).isNull();
	}

	@Test
	void should_return_span_when_brave_span_present_in_context() {
		Span span = this.tracer.nextSpan();

		then(BraveSpan.toBrave(retriever.findSpan(Context.of(Span.class, span)))).isSameAs(span);
	}

	@Test
	void should_return_span_when_brave_trace_context_present_in_context() {
		Span span = this.tracer.nextSpan();

		then(BraveSpan.toBrave(retriever.findSpan(Context.of(TraceContext.class, span.context())))).isEqualTo(span);
	}

}
