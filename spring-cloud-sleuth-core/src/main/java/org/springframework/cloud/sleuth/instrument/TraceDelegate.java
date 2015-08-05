/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument;

import lombok.Getter;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceContextHolder;
import org.springframework.cloud.sleuth.TraceScope;

/**
 * @author Spencer Gibb
 */
@Getter
public abstract class TraceDelegate<T> {

	private final Trace trace;
	private final T delegate;
	private final Span parent;
	private final String name;

	public TraceDelegate(Trace trace, T delegate) {
		this(trace, delegate, TraceContextHolder.getCurrentSpan(), null);
	}

	public TraceDelegate(Trace trace, T delegate, Span parent) {
		this(trace, delegate, parent, null);
	}

	public TraceDelegate(Trace trace, T delegate, Span parent, String name) {
		this.trace = trace;
		this.delegate = delegate;
		this.parent = parent;
		this.name = name;
	}

	protected TraceScope startSpan() {
		return this.trace.startSpan(getSpanName(), this.parent);
	}

	protected String getSpanName() {
		return this.name == null ? Thread.currentThread().getName() : this.name;
	}
}
