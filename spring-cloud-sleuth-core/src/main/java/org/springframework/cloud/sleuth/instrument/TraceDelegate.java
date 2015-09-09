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

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;

import lombok.Getter;

/**
 * @author Spencer Gibb
 */
@Getter
public abstract class TraceDelegate<T> {

	private final TraceManager traceManager;
	private final T delegate;
	private final String name;
	private final Span parent;

	public TraceDelegate(TraceManager trace, T delegate) {
		this(trace, delegate, null);
	}

	public TraceDelegate(TraceManager traceManager, T delegate, String name) {
		this.traceManager = traceManager;
		this.delegate = delegate;
		this.name = name;
		this.parent = traceManager.getCurrentSpan();
	}

	protected void close(Trace scope) {
		this.traceManager.close(scope);
	}

	protected Trace startSpan() {
		return this.traceManager.startSpan(getSpanName(), this.parent);
	}

	protected String getSpanName() {
		return this.name == null ? Thread.currentThread().getName() : this.name;
	}
}
