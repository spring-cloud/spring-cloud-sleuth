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
 * The TraceManager class is the primary way for instrumentation code (note user code) to
 * interact with the library. It provides methods to create and manipulate spans.
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
 * The 'startTrace' method in this class starts a new span.
 *
 * <li>Create a TraceSpan object to manage the new Span.</li>
 * </ul>
 *
 * The 'joinTrace' method creates a new Span which has this thread's currentSpan as one of its parents
 *
 * Closing a TraceScope does a few things:
 * <ul>
 * <li>It closes the span which the scope was managing.</li>
 * <li>Set currentSpan to the previous currentSpan (which may be null).</li>
 * </ul>
 */
public interface Tracer extends SpanAccessor {

	/**
	 * Creates a new Span.
	 * <p/>
	 * If this thread has a currently active span, it will be the parent of the span we
	 * create here. If there is no currently active trace span, the trace scope we
	 * create will be empty.
	 *
	 * @param name The name field for the new span to create.
	 */
	Span startTrace(String name);

	/**
	 * Creates a new Span with a specific parent. The parent might be in another
	 * process or thread.
	 * <p/>
	 * If this thread has a currently active trace span, it must be the 'parent' span that
	 * you pass in here as a parameter. The trace scope we create here will contain a new
	 * span which is a child of 'parent'.
	 *
	 * @param name The name field for the new span to create.
	 */
	Span joinTrace(String name, Span parent);

	/**
	 * Start a new span if the sampler allows it or if we are already tracing in this
	 * thread. A sampler can be used to limit the number of traces created.
	 *  @param name the name of the span
	 * @param sampler a sampler to decide whether to create the span or not
	 */
	Span startTrace(String name, Sampler sampler);

	/**
	 * Pick up an existing span from another thread.
	 */
	Span continueSpan(Span span);

	/**
	 * Adds a tag to the current span if tracing is currently on.
	 */
	void addTag(String key, String value);

	/**
	 * Remove this span from the current thread, but don't stop it yet or send it for
	 * collection. This is useful if the span object is then passed to another thread for
	 * use with Span.continueTrace().
	 *
	 * @return the saved trace if there was one before the trace started (null otherwise)
	 */
	Span detach(Span span);

	/**
	 * Remove this span from the current thread, stop it and send it for collection.
	 *
	 * @param span the span to close
	 * @return the saved span if there was one before the trace started (null otherwise)
	 */
	Span close(Span span);

	<V> Callable<V> wrap(Callable<V> callable);

	Runnable wrap(Runnable runnable);
}
