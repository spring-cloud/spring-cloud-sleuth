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

import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import org.springframework.cloud.sleuth.CurrentTraceContext;

import static org.assertj.core.api.BDDAssertions.then;

class BraveCurrentTraceContextTests {

	ThreadLocalCurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
			.addScopeDecorator(MDCScopeDecorator.newBuilder().build()).build();

	@Test
	void should_clear_any_thread_locals_and_scopes_when_null_context_passed_to_new_scope() {
		BraveCurrentTraceContext braveCurrentTraceContext = new BraveCurrentTraceContext(currentTraceContext);
		brave.propagation.CurrentTraceContext.Scope newScope = currentTraceContext
				.newScope(TraceContext.newBuilder().traceId(12345678).spanId(12345678).build());
		then(currentTraceContext.get()).isNotNull();

		CurrentTraceContext.Scope scope = braveCurrentTraceContext.newScope(null);

		thenThreadLocalsGotCleared(braveCurrentTraceContext, scope);
		newScope.close();
		thenThreadLocalsGotCleared(braveCurrentTraceContext, scope);
	}

	@Test
	void should_clear_any_thread_locals_and_scopes_when_null_context_passed_to_new_scope_with_nested_scopes() {
		BraveCurrentTraceContext braveCurrentTraceContext = new BraveCurrentTraceContext(currentTraceContext);

		try (CurrentTraceContext.Scope scope1 = braveCurrentTraceContext.newScope(
				BraveTraceContext.fromBrave(TraceContext.newBuilder().traceId(12345678).spanId(12345670).build()))) {
			then(currentTraceContext.get()).isNotNull();
			thenMdcEntriesArePresent();
			try (CurrentTraceContext.Scope scope2 = braveCurrentTraceContext.newScope(BraveTraceContext
					.fromBrave(TraceContext.newBuilder().traceId(12345678).spanId(12345671).build()))) {
				then(currentTraceContext.get()).isNotNull();
				thenMdcEntriesArePresent();
				try (CurrentTraceContext.Scope scope3 = braveCurrentTraceContext.newScope(BraveTraceContext
						.fromBrave(TraceContext.newBuilder().traceId(12345678).spanId(12345672).build()))) {
					then(currentTraceContext.get()).isNotNull();
					thenMdcEntriesArePresent();
					try (CurrentTraceContext.Scope nullScope = braveCurrentTraceContext.newScope(null)) {
						// This closes all scopes and MDC entries
						then(currentTraceContext.get()).isNull();
					}
					// We have nothing to revert to since the nullScope is ignoring
					// everything there was before
					then(currentTraceContext.get()).isNull();
					thenMdcEntriesAreMissing();
				}
				then(currentTraceContext.get()).isNull();
				thenMdcEntriesAreMissing();
			}
			then(currentTraceContext.get()).isNull();
			thenMdcEntriesAreMissing();
		}

		then(currentTraceContext.get()).isNull();
		then(braveCurrentTraceContext.scopes.get()).isNull();
		then(MDC.getCopyOfContextMap()).isEmpty();
	}

	@Test
	void should_clear_any_thread_locals_and_scopes_when_null_context_passed_to_maybe_scope_with_nested_scopes() {
		BraveCurrentTraceContext braveCurrentTraceContext = new BraveCurrentTraceContext(currentTraceContext);

		try (CurrentTraceContext.Scope scope1 = braveCurrentTraceContext.maybeScope(
				BraveTraceContext.fromBrave(TraceContext.newBuilder().traceId(12345678).spanId(12345670).build()))) {
			then(currentTraceContext.get()).isNotNull();
			thenMdcEntriesArePresent();
			try (CurrentTraceContext.Scope scope2 = braveCurrentTraceContext.maybeScope(BraveTraceContext
					.fromBrave(TraceContext.newBuilder().traceId(12345678).spanId(12345671).build()))) {
				then(currentTraceContext.get()).isNotNull();
				thenMdcEntriesArePresent();
				try (CurrentTraceContext.Scope scope3 = braveCurrentTraceContext.maybeScope(BraveTraceContext
						.fromBrave(TraceContext.newBuilder().traceId(12345678).spanId(12345672).build()))) {
					then(currentTraceContext.get()).isNotNull();
					thenMdcEntriesArePresent();
					try (CurrentTraceContext.Scope nullScope = braveCurrentTraceContext.maybeScope(null)) {
						// This closes all scopes and MDC entries
						then(currentTraceContext.get()).isNull();
					}
					// We have nothing to revert to since the nullScope is ignoring
					// everything there was before
					then(currentTraceContext.get()).isNull();
					thenMdcEntriesAreMissing();
				}
				then(currentTraceContext.get()).isNull();
				thenMdcEntriesAreMissing();
			}
			then(currentTraceContext.get()).isNull();
			thenMdcEntriesAreMissing();
		}

		then(currentTraceContext.get()).isNull();
		then(braveCurrentTraceContext.scopes.get()).isNull();
		then(MDC.getCopyOfContextMap()).isEmpty();
	}

	private static void thenMdcEntriesArePresent() {
		then(MDC.get("traceId")).isEqualTo("0000000000bc614e");
		then(MDC.get("spanId")).isNotEmpty();
	}

	private static void thenMdcEntriesAreMissing() {
		then(MDC.getCopyOfContextMap()).isEmpty();
	}

	@Test
	void should_clear_any_thread_locals_and_scopes_when_null_context_passed_to_maybe_scope() {
		BraveCurrentTraceContext braveCurrentTraceContext = new BraveCurrentTraceContext(currentTraceContext);
		brave.propagation.CurrentTraceContext.Scope maybeScope = currentTraceContext
				.newScope(TraceContext.newBuilder().traceId(12345678).spanId(12345678).build());
		then(currentTraceContext.get()).isNotNull();

		CurrentTraceContext.Scope scope = braveCurrentTraceContext.maybeScope(null);

		thenThreadLocalsGotCleared(braveCurrentTraceContext, scope);
		maybeScope.close();
		thenThreadLocalsGotCleared(braveCurrentTraceContext, scope);
	}

	private void thenThreadLocalsGotCleared(BraveCurrentTraceContext braveCurrentTraceContext,
			CurrentTraceContext.Scope scope) {
		then(scope).isSameAs(CurrentTraceContext.Scope.NOOP);
		then(currentTraceContext.get()).isNull();
		then(braveCurrentTraceContext.scopes.get()).isNull();
	}

}
