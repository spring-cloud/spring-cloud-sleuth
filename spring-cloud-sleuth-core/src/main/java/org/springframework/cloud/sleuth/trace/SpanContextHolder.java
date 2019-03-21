/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.trace;

import org.apache.commons.logging.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.core.NamedThreadLocal;

/**
 * Utility for managing the thread local state for the {@link DefaultTracer}.
 *
 * @author Spencer Gibb
 * @author Dave Syer
 */
class SpanContextHolder {

	private static final Log log = org.apache.commons.logging.LogFactory
			.getLog(SpanContextHolder.class);
	private static final ThreadLocal<SpanContext> CURRENT_SPAN = new NamedThreadLocal<>(
			"Trace Context");

	/**
	 * Get the current span out of the thread context
	 */
	static Span getCurrentSpan() {
		return isTracing() ? CURRENT_SPAN.get().span : null;
	}

	/**
	 * Set the current span in the thread context
	 */
	static void setCurrentSpan(Span span) {
		if (log.isTraceEnabled()) {
			log.trace("Setting current span " + span);
		}
		push(span, false);
	}

	/**
	 * Remove all thread context relating to spans (useful for testing).
	 *
	 * @see #close() for a better alternative in instrumetation
	 */
	static void removeCurrentSpan() {
		CURRENT_SPAN.remove();
	}

	/**
	 * Check if there is already a span in the current thread
	 */
	static boolean isTracing() {
		return CURRENT_SPAN.get() != null;
	}

	/**
	 * Close the current span and all parents that can be auto closed.
	 * On every iteration a function will be applied on the closed Span.
	 */
	static void close(SpanFunction spanFunction) {
		SpanContext current = CURRENT_SPAN.get();
		CURRENT_SPAN.remove();
		while (current != null) {
			current = current.parent;
			spanFunction.apply(current != null ? current.span : null);
			if (current != null) {
				if (!current.autoClose) {
					CURRENT_SPAN.set(current);
					current = null;
				}
			}
		}
	}

	/**
	 * Close the current span and all parents that can be auto closed.
	 */
	static void close() {
		close(new NoOpFunction());
	}

	/**
	 * Push a span into the thread context, with the option to have it auto close if any
	 * child spans are themselves closed. Use autoClose=true if you start a new span with
	 * a parent that wasn't already in thread context.
	 */
	static void push(Span span, boolean autoClose) {
		if (isCurrent(span)) {
			return;
		}
		CURRENT_SPAN.set(new SpanContext(span, autoClose));
	}

	private static boolean isCurrent(Span span) {
		if (span == null || CURRENT_SPAN.get() == null) {
			return false;
		}
		return span.equals(CURRENT_SPAN.get().span);
	}

	private static class SpanContext {
		final Span span;
		final boolean autoClose;
		final SpanContext parent;

		public SpanContext(Span span, boolean autoClose) {
			this.span = span;
			this.autoClose = autoClose;
			this.parent = CURRENT_SPAN.get();
		}
	}

	interface SpanFunction {
		void apply(Span span);
	}

	private static class NoOpFunction implements SpanFunction {
		@Override public void apply(Span span) { }
	}
}
