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

import java.util.concurrent.Callable;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import org.springframework.cloud.brave.SpanNamer;

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

	private final Tracing tracing;
	private final SpanNamer spanNamer;
	private final Callable<V> delegate;
	private final String name;
	private final Span parent;

	public TraceCallable(Tracing tracing,  SpanNamer spanNamer, Callable<V> delegate) {
		this(tracing, spanNamer, delegate, null);
	}

	public TraceCallable(Tracing tracing, SpanNamer spanNamer, Callable<V> delegate, String name) {
		this.tracing = tracing;
		this.spanNamer = spanNamer;
		this.delegate = delegate;
		this.name = name;
		this.parent = tracing.tracer().currentSpan();
	}

	@Override
	public V call() throws Exception {
		Span span = startSpan();
		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			return this.getDelegate().call();
		}
		finally {
			close(span);
		}
	}

	protected Span startSpan() {
		if (this.parent != null) {
			return this.tracing.tracer().newChild(this.parent.context()).name(getSpanName()).start();
		}
		return this.tracing.tracer().nextSpan().name(getSpanName()).start();
	}

	protected String getSpanName() {
		if (this.name != null) {
			return this.name;
		}
		return this.spanNamer.name(this.delegate, "async");
	}

	protected void close(Span span) {
		span.finish();
	}

	protected Span continueSpan(Span span) {
		return this.tracing.tracer().joinSpan(span.context());
	}

	protected void detachSpan(Span span) {
		span.abandon();
	}

	public Tracing getTracing() {
		return this.tracing;
	}

	public Callable<V> getDelegate() {
		return this.delegate;
	}

	public String getName() {
		return this.name;
	}

	public Span getParent() {
		return this.parent;
	}

}
