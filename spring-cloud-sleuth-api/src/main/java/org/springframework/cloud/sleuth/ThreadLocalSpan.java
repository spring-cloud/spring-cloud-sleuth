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

package org.springframework.cloud.sleuth;

import java.util.ArrayDeque;

/**
 * Represents a {@link Span} stored in thread local.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class ThreadLocalSpan {

	private final ThreadLocal<ArrayDeque<SpanAndScope>> currentSpanInScopeStack = new ThreadLocal<>();

	private final Tracer tracer;

	public ThreadLocalSpan(Tracer tracer) {
		this.tracer = tracer;
	}

	/**
	 * Sets given span and scope.
	 * @param span - span to be put in scope
	 */
	public void set(Span span) {
		Tracer.SpanInScope spanInScope = this.tracer.withSpan(span);
		SpanAndScope newSpanAndScope = new SpanAndScope(span, spanInScope);
		getCurrentSpanInScopeStack().addFirst(newSpanAndScope);
	}

	/**
	 * @return currently stored span and scope
	 */
	public SpanAndScope get() {
		return getCurrentSpanInScopeStack().peekFirst();
	}

	/**
	 * Removes the current span from thread local and brings back the previous span to the
	 * current thread local.
	 */
	public void remove() {
		SpanAndScope spanAndScope = getCurrentSpanInScopeStack().pollFirst();
		if (spanAndScope == null) {
			return;
		}
		if (spanAndScope.getScope() != null) {
			spanAndScope.getScope().close();
		}
	}

	private ArrayDeque<SpanAndScope> getCurrentSpanInScopeStack() {
		ArrayDeque<SpanAndScope> stack = this.currentSpanInScopeStack.get();
		if (stack == null) {
			stack = new ArrayDeque<>();
			this.currentSpanInScopeStack.set(stack);
		}
		return stack;
	}

}
