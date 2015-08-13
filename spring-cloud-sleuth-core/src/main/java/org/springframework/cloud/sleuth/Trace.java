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

package org.springframework.cloud.sleuth;

import java.util.concurrent.Callable;

/**
 * The Trace class is the primary way to interact with the library. It provides methods to
 * create and manipulate spans.
 *
 * A 'Span' represents a length of time. It has many other attributes such as a name, ID,
 * and even potentially a set of key/value strings attached to it.
 *
 * Each thread in your application has a single currently active currentSpan associated
 * with it. When this is non-null, it represents the current operation that the thread is
 * doing. Spans are NOT thread-safe, and must never be used by multiple threads at once.
 * With care, it is possible to safely pass a Span object between threads, but in most
 * cases this is not necessary.
 *
 * A 'TraceScope' can either be empty, or contain a Span. TraceScope objects implement the
 * Java's Closeable interface. Similar to file descriptors, they must be closed after they
 * are created. When a TraceScope contains a Span, this span is closed when the scope is
 * closed.
 *
 * The 'startSpan' methods in this class do a few things:
 * <ul>
 * <li>Create a new Span which has this thread's currentSpan as one of its parents.</li>
 * <li>Set currentSpan to the new Span.</li>
 * <li>Create a TraceSpan object to manage the new Span.</li>
 * </ul>
 *
 * Closing a TraceScope does a few things:
 * <ul>
 * <li>It closes the span which the scope was managing.</li>
 * <li>Set currentSpan to the previous currentSpan (which may be null).</li>
 * </ul>
 */
public interface Trace {

	String SPAN_ID_NAME = "X-Span-Id";
	String TRACE_ID_NAME = "X-Trace-Id";
	String SPAN_NAME_NAME = "X-Span-Name";
	String PARENT_ID_NAME = "X-Parent-Id";
	String PROCESS_ID_NAME = "X-Process-Id";
	String NOT_SAMPLED_NAME = "X-Not-Sampled";

	/**
	 * Creates a trace scope wrapping a new span.
	 * <p/>
	 * If this thread has a currently active span, it will be the parent of the span we
	 * create here, and the trace scope will contain the new span and the parent. If there
	 * is no currently active trace span, the trace scope we create will be empty.
	 *
	 * @param name The name field for the new span to create.
	 */
	TraceScope startSpan(String name);

	/**
	 * Creates a new trace scope with a specific parent. The parent might be in another
	 * process or thread.
	 * <p/>
	 * If this thread has a currently active trace span, it must be the 'parent' span that
	 * you pass in here as a parameter. The trace scope we create here will contain a new
	 * span which is a child of 'parent'.
	 *
	 * @param name The name field for the new span to create.
	 */
	TraceScope startSpan(String name, Span parent);

	/**
	 * Start a new span if the sampler allows it or if we are already tracing in this
	 * thread. A sampler can be used to limit the number of traces created.
	 *
	 * @param name the name of the span
	 * @param sampler a sampler to decide whether to create the span or not
	 * @param info the samplers context information
	 */
	<T> TraceScope startSpan(String name, Sampler<T> sampler, T info);

	/**
	 * Pick up an existing span from another thread.
	 */
	TraceScope continueSpan(Span s);

	/**
	 * Adds a data annotation to the current span if tracing is currently on.
	 */
	void addKVAnnotation(String key, String value);

	<V> Callable<V> wrap(Callable<V> callable);

	Runnable wrap(Runnable runnable);
}
