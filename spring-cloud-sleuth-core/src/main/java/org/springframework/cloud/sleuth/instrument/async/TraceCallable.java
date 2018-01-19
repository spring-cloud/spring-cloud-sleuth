/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Callable;

import brave.Span;
import brave.Tracer;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.SpanNamer;

/**
 * Callable that passes Span between threads. The Span name is
 * taken either from the passed value or from the {@link SpanNamer}
 * interface.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class TraceCallable<V> implements Callable<V> {

	/**
	 * Since we don't know the exact operation name we provide a default
	 * name for the Span
	 */
	private static final String DEFAULT_SPAN_NAME = "async";

	private final Tracer tracer;
	private final Callable<V> delegate;
	private final Span span;
	private final ErrorParser errorParser;

	public TraceCallable(Tracer tracer, SpanNamer spanNamer, ErrorParser errorParser, Callable<V> delegate) {
		this(tracer, spanNamer, errorParser, delegate, null);
	}

	public TraceCallable(Tracer tracer, SpanNamer spanNamer, ErrorParser errorParser, Callable<V> delegate, String name) {
		this.tracer = tracer;
		this.delegate = delegate;
		String spanName = name != null ? name : spanNamer.name(delegate, DEFAULT_SPAN_NAME);
		this.span = this.tracer.nextSpan().name(spanName);
		this.errorParser = errorParser;
	}

	@Override public V call() throws Exception {
		Throwable error = null;
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(this.span.start())) {
			return this.delegate.call();
		} catch (Exception | Error e) {
			error = e;
			throw e;
		} finally {
			this.errorParser.parseErrorTags(this.span, error);
			this.span.finish();
		}
	}
}
