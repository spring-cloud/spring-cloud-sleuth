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

package org.springframework.cloud.sleuth.instrument.async;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;

/**
 * @author Spencer Gibb
 */
public abstract class LocalComponentTraceDelegate<T> extends TraceDelegate<T> {

	private final TraceKeys traceKeys;

	public LocalComponentTraceDelegate(Tracer tracer, TraceKeys traceKeys, T delegate) {
		super(tracer, delegate, null);
		this.traceKeys = traceKeys;
	}

	public LocalComponentTraceDelegate(Tracer tracer, TraceKeys traceKeys, T delegate, String name) {
		super(tracer, delegate, name);
		this.traceKeys = traceKeys;
	}

	@Override
	protected Span startSpan() {
		Span span = getTracer().joinTrace(getSpanName(), getParent());
		getTracer().addTag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, ASYNC_COMPONENT);
		getTracer().addTag(this.traceKeys.getAsync().getPrefix() +
				this.traceKeys.getAsync().getThreadNameKey(), Thread.currentThread().getName());
		return span;
	}

}
