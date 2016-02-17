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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Callable;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

/**
 * @author Spencer Gibb
 */
public class TraceCallable<V> implements Callable<V> {

	protected static final String ASYNC_COMPONENT = "async";

	private final Tracer tracer;
	private final Callable<V> delegate;
	private final String name;
	private final Span parent;

	public TraceCallable(Tracer tracer, Callable<V> delegate) {
		this(tracer, delegate, null);
	}

	public TraceCallable(Tracer tracer, Callable<V> delegate, String name) {
		this.tracer = tracer;
		this.delegate = delegate;
		this.name = name;
		this.parent = tracer.getCurrentSpan();
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

	protected Span startSpan() {
		return this.tracer.joinTrace(getSpanName(), this.parent);
	}

	protected String getSpanName() {
		return this.name == null ?
				ASYNC_COMPONENT
				: this.name;
	}

	protected void close(Span span) {
		this.tracer.close(span);
	}

	public Tracer getTracer() {
		return this.tracer;
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
