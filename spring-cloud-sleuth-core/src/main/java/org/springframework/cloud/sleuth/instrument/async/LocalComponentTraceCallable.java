/*
 * Copyright 2013-2017 the original author or authors.
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

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.TraceCallable;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.TraceKeys;

/**
 * Callable that starts a span that is a local component span.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class LocalComponentTraceCallable<V> extends TraceCallable<V> {

	protected static final String ASYNC_COMPONENT = "async";

	private final TraceKeys traceKeys;

	public LocalComponentTraceCallable(Tracer tracer, TraceKeys traceKeys,
			SpanNamer spanNamer, Callable<V> delegate) {
		super(tracer, spanNamer, delegate);
		this.traceKeys = traceKeys;
	}

	public LocalComponentTraceCallable(Tracer tracer, TraceKeys traceKeys,
			SpanNamer spanNamer, String name, Callable<V> delegate) {
		super(tracer, spanNamer, delegate, name);
		this.traceKeys = traceKeys;
	}

	@Override
	public V call() throws Exception {
		Span span = startSpan();
		try {
			return this.getDelegate().call();
		}
		finally {
			close(span);
		}
	}

	@Override
	protected Span startSpan() {
		Span span = getTracer().createSpan(getSpanName(), getParent());
		getTracer().addTag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, ASYNC_COMPONENT);
		getTracer().addTag(this.traceKeys.getAsync().getPrefix() +
				this.traceKeys.getAsync().getThreadNameKey(), Thread.currentThread().getName());
		return span;
	}
}
