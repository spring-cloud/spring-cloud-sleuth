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
