/*
 * Copyright 2013-2023 the original author or authors.
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

import brave.Tracing;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

import static org.assertj.core.api.BDDAssertions.then;

class BraveTracerTests {

	ThreadLocalCurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
			.addScopeDecorator(MDCScopeDecorator.newBuilder().build()).build();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(currentTraceContext).build();

	BraveCurrentTraceContext braveCurrentTraceContext = new BraveCurrentTraceContext(currentTraceContext);

	BraveTracer braveTracer = new BraveTracer(tracing.tracer(), braveCurrentTraceContext, new BraveBaggageManager());

	@Test
	void should_clear_any_thread_locals_and_scopes_when_null_context_passed_to_with_span() {
		Span span = braveTracer.nextSpan();
		Tracer.SpanInScope newScope = braveTracer.withSpan(span.start());
		then(braveTracer.currentSpan()).isEqualTo(span);

		Tracer.SpanInScope noopScope = braveTracer.withSpan(null);

		thenThreadLocalsGotCleared(braveCurrentTraceContext, noopScope);
		newScope.close();
		thenThreadLocalsGotCleared(braveCurrentTraceContext, noopScope);
	}

	@Test
	void should_clear_any_thread_locals_and_scopes_when_null_context_passed_to_with_span_with_nested_scopes() {
		Span nextSpan1 = braveTracer.nextSpan();
		try (Tracer.SpanInScope scope1 = braveTracer.withSpan(nextSpan1.start())) {
			then(braveTracer.currentSpan()).isEqualTo(nextSpan1);
			thenMdcEntriesArePresent(nextSpan1.context());
			Span nextSpan2 = braveTracer.nextSpan();
			try (Tracer.SpanInScope scope2 = braveTracer.withSpan(nextSpan2.start())) {
				then(braveTracer.currentSpan()).isEqualTo(nextSpan2);
				thenMdcEntriesArePresent(nextSpan2.context());
				Span nextSpan3 = braveTracer.nextSpan();
				try (Tracer.SpanInScope scope3 = braveTracer.withSpan(nextSpan3.start())) {
					then(braveTracer.currentSpan()).isEqualTo(nextSpan3);
					thenMdcEntriesArePresent(nextSpan3.context());
					try (Tracer.SpanInScope nullScope = braveTracer.withSpan(null)) {
						// This closes all scopes and MDC entries
						then(braveTracer.currentSpan()).isNull();
						then(currentTraceContext.get()).isNull();
					}
					// We have nothing to revert to since the nullScope is ignoring
					// everything there was before
					then(braveTracer.currentSpan()).isNull();
					then(currentTraceContext.get()).isNull();
					thenMdcEntriesAreMissing();
					nextSpan3.end();
				}
				then(braveTracer.currentSpan()).isNull();
				then(currentTraceContext.get()).isNull();
				thenMdcEntriesAreMissing();
				nextSpan2.end();
			}
			then(braveTracer.currentSpan()).isNull();
			then(currentTraceContext.get()).isNull();
			thenMdcEntriesAreMissing();
			nextSpan1.end();
		}

		then(currentTraceContext.get()).isNull();
		then(braveTracer.currentSpan()).isNull();
		then(MDC.getCopyOfContextMap()).isEmpty();
	}

	private static void thenMdcEntriesArePresent(org.springframework.cloud.sleuth.TraceContext traceContext) {
		then(MDC.get("traceId")).isEqualTo(traceContext.traceId());
		then(MDC.get("spanId")).isEqualTo(traceContext.spanId());
	}

	private static void thenMdcEntriesAreMissing() {
		then(MDC.getCopyOfContextMap()).isEmpty();
	}

	private void thenThreadLocalsGotCleared(BraveCurrentTraceContext braveCurrentTraceContext,
			Tracer.SpanInScope scope) {
		then(scope).isSameAs(Tracer.SpanInScope.NOOP);
		then(braveTracer.currentSpan()).isNull();
		then(braveCurrentTraceContext.context()).isNull();
	}

}
