package org.springframework.cloud.sleuth.instrument;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceContextHolder;
import org.springframework.cloud.sleuth.TraceScope;

/**
 * @author Spencer Gibb
 */
public abstract class TraceDelegate<T> {

	protected final Trace trace;
	protected final T delagate;
	protected final Span parent;
	protected final String name;
	
	public TraceDelegate(Trace trace, T delagate) {
		this(trace, delagate, TraceContextHolder.getCurrentSpan(), null);
	}

	public TraceDelegate(Trace trace, T delagate, Span parent) {
		this(trace, delagate, parent, null);
	}

	public TraceDelegate(Trace trace, T delagate, Span parent, String name) {
		this.trace = trace;
		this.delagate = delagate;
		this.parent = parent;
		this.name = name;
	}

	protected TraceScope startSpan() {
		return this.startSpan(Span.Type.CLIENT);
	}

	protected TraceScope startSpan(Span.Type type) {
		return this.trace.startSpan(type, getSpanName(), this.parent);
	}

	protected String getSpanName() {
		return this.name == null ? Thread.currentThread().getName() : name;
	}
}
