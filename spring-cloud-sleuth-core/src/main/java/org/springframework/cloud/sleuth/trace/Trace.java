package org.springframework.cloud.sleuth.trace;

import java.util.Collection;

/**
 * The Trace class is the primary way to interact with the library.  It provides
 * methods to create and manipulate spans.
 *
 * A 'Span' represents a length of time.  It has many other attributes such as a
 * description, ID, and even potentially a set of key/value strings attached to
 * it.
 *
 * Each thread in your application has a single currently active currentSpan
 * associated with it.  When this is non-null, it represents the current
 * operation that the thread is doing.  Spans are NOT thread-safe, and must
 * never be used by multiple threads at once.  With care, it is possible to
 * safely pass a Span object between threads, but in most cases this is not
 * necessary.
 *
 * A 'TraceScope' can either be empty, or contain a Span.  TraceScope objects
 * implement the Java's Closeable interface.  Similar to file descriptors, they
 * must be closed after they are created.  When a TraceScope contains a Span,
 * this span is closed when the scope is closed.
 *
 * The 'startSpan' methods in this class do a few things:
 * <ul>
 *   <li>Create a new Span which has this thread's currentSpan as one of its parents.</li>
 *   <li>Set currentSpan to the new Span.</li>
 *   <li>Create a TraceSpan object to manage the new Span.</li>
 * </ul>
 *
 * Closing a TraceScope does a few things:
 * <ul>
 *   <li>It closes the span which the scope was managing.</li>
 *   <li>Set currentSpan to the previous currentSpan (which may be null).</li>
 * </ul>
 */
public interface Trace {

	/**
	 * Creates a new trace scope.
	 * <p/>
	 * If this thread has a currently active trace span, the trace scope we create
	 * here will contain a new span descending from the currently active span.
	 * If there is no currently active trace span, the trace scope we create will
	 * be empty.
	 *
	 * @param description The description field for the new span to create.
	 */
	TraceScope startSpan(String description);

	TraceScope startSpan(String description, TraceInfo tinfo);

	/**
	 * Creates a new trace scope.
	 * <p/>
	 * If this thread has a currently active trace span, it must be the 'parent'
	 * span that you pass in here as a parameter.  The trace scope we create here
	 * will contain a new span which is a child of 'parent'.
	 *
	 * @param description The description field for the new span to create.
	 */
	TraceScope startSpan(String description, Span parent);

	<T> TraceScope startSpan(String description, Sampler<T> s);

	<T> TraceScope startSpan(String description, Sampler<T> s, T info);

	/**
	 * Pick up an existing span from another thread.
	 */
	TraceScope continueSpan(Span s);

	/**
	 * Adds a data annotation to the current span if tracing is currently on.
	 */
	void addKVAnnotation(String key, String value);

	/**
	 * Returns true if the current thread is a part of a trace, false otherwise.
	 */
	boolean isTracing();

	/**
	 * If we are tracing, return the current span, else null
	 *
	 * @return Span representing the current trace, or null if not tracing.
	 */
	Span currentSpan();

	void setSpanReceivers(Collection<SpanReceiver> spanReceivers);
}
