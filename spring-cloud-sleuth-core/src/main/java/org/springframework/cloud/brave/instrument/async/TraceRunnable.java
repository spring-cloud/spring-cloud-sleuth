/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.brave.instrument.async;

import brave.Span;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import org.springframework.cloud.brave.SpanNamer;

/**
 * Runnable that passes Span between threads. The Span name is
 * taken either from the passed value or from the {@link SpanNamer}
 * interface.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class TraceRunnable implements Runnable {

	/**
	 * Since we don't know the exact operation name we provide a default
	 * name for the Span
	 */
	static final String DEFAULT_SPAN_NAME = "async";

	private final Tracing tracing;
	private final Runnable delegate;
	private final Span span;

	public TraceRunnable(Tracing tracing, SpanNamer spanNamer, Runnable delegate) {
		this(tracing, spanNamer, delegate, null);
	}

	public TraceRunnable(Tracing tracing, SpanNamer spanNamer, Runnable delegate, String name) {
		this.tracing = tracing;
		this.delegate = delegate;
		String spanName = name != null ? name : spanNamer.name(delegate, DEFAULT_SPAN_NAME);
		this.span = this.tracing.tracer().nextSpan().name(spanName);
	}

	@Override
	public void run() {
		  Throwable error = null;
			try (SpanInScope ws = this.tracing.tracer().withSpanInScope(this.span.start())) {
				this.delegate.run();
			} catch (RuntimeException | Error e) {
				error = e;
				throw e;
			} finally {
				if (error != null) {
					String message = error.getMessage();
					if (message == null) message = error.getClass().getSimpleName();
					this.span.tag("error", message);
				}
				this.span.finish();
			}
	}
}
