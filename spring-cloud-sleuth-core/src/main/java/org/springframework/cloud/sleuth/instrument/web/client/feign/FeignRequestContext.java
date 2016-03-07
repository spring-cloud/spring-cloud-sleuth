/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.springframework.cloud.sleuth.Span;

/**
 * Class that holds the information for the span processed by the current
 * request. It also knows whether the request has already been retried.
 *
 * The implementation works on a {@link ThreadLocal} thus is thread-safe.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class FeignRequestContext {

	private static final FeignRequestContext INSTANCE = new FeignRequestContext();

	private FeignRequestContext() {}

	private static final ThreadLocal<SpanHolder> THREAD_LOCAL = new ThreadLocal<>();

	private static final class SpanHolder {
		final Span span;
		final boolean retried;

		private SpanHolder(Span span, boolean retried) {
			this.span = span;
			this.retried = retried;
		}
	}

	boolean hasSpanInProcess() {
		return THREAD_LOCAL.get() != null;
	}

	Span getCurrentSpan() {
		if (hasSpanInProcess()) {
			return THREAD_LOCAL.get().span;
		}
		return null;
	}

	boolean wasSpanRetried() {
		return hasSpanInProcess() && THREAD_LOCAL.get().retried;
	}

	void putSpan(Span span, boolean retried) {
		THREAD_LOCAL.set(new SpanHolder(span, retried));
	}

	void clearContext() {
		THREAD_LOCAL.remove();
	}

	static FeignRequestContext getInstance() {
		return INSTANCE;
	}
}
