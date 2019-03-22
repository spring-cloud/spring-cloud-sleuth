/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Callable;

import brave.ScopedSpan;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;

import org.springframework.cloud.sleuth.SpanNamer;

/**
 * Callable that passes Span between threads. The Span name is taken either from the
 * passed value or from the {@link SpanNamer} interface.
 *
 * @param <V> - return type from callable
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class TraceCallable<V> implements Callable<V> {

	/**
	 * Since we don't know the exact operation name we provide a default name for the
	 * Span.
	 */
	private static final String DEFAULT_SPAN_NAME = "async";

	private final Tracer tracer;

	private final Callable<V> delegate;

	private final TraceContext parent;

	private final String spanName;

	public TraceCallable(Tracing tracing, SpanNamer spanNamer, Callable<V> delegate) {
		this(tracing, spanNamer, delegate, null);
	}

	public TraceCallable(Tracing tracing, SpanNamer spanNamer, Callable<V> delegate,
			String name) {
		this.tracer = tracing.tracer();
		this.delegate = delegate;
		this.parent = tracing.currentTraceContext().get();
		this.spanName = name != null ? name : spanNamer.name(delegate, DEFAULT_SPAN_NAME);
	}

	@Override
	public V call() throws Exception {
		ScopedSpan span = this.tracer.startScopedSpanWithParent(this.spanName,
				this.parent);
		try {
			return this.delegate.call();
		}
		catch (Exception | Error ex) {
			span.error(ex);
			throw ex;
		}
		finally {
			span.finish();
		}
	}

}
