/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth;

import java.util.concurrent.Callable;

/**
 * The Tracer class is the primary way for instrumentation code (note user code) to
 * interact with the library. It provides methods to create and manipulate spans.
 * <p>
 *
 * A 'span' represents a length of time. It has many other attributes such as a name, ID,
 * and even potentially a set of key/value strings attached to it.
 * <p>
 *
 * Each thread in your application has a single currently active currentSpan associated
 * with it. When this is non-null, it represents the current operation that the thread is
 * doing. spans are NOT thread-safe, and must never be used by multiple threads at once.
 * With care, it is possible to safely pass a span object between threads, but in most
 * cases this is not necessary.
 * <p>
 *
 * Most crucial methods in terms of span lifecycle are:
 * <ul>
 * <li>The {@linkplain Tracer#createSpan(String) createSpan} method in this class
 * starts a new span.</li>
 * <li>The {@linkplain Tracer#createSpan(String, Span) createSpan} method creates a new span
 * which has this thread's currentSpan as one of its parents</li>
 * <li>The {@linkplain Tracer#continueSpan(Span) continueSpan} method creates a
 * new instance of span that logically is a continuation of the provided span.</li>
 * </ul>
 *
 * Closing a TraceScope does a few things:
 * <ul>
 * <li>It closes the span which the scope was managing.</li>
 * <li>Set currentSpan to the previous currentSpan (which may be null).</li>
 * </ul>
 *
 * @since 1.0.0
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
	Span createSpan(String name);

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
	Span createSpan(String name, Span parent);

	/**
	 * Start a new span if the sampler allows it or if we are already tracing in this
	 * thread. A sampler can be used to limit the number of traces created.
	 *
	 * @param name the name of the span
	 * @param sampler a sampler to decide whether to create the span or not
	 */
	Span createSpan(String name, Sampler sampler);

	/**
	 * Contributes to a span started in another thread. The returned span shares
	 * mutable state with the input.
	 */
	Span continueSpan(Span span);

	/**
	 * Adds a tag to the current span if tracing is currently on.
	 * <p>
	 * Every span may also have zero or more key/value Tags, which do not have
	 * timestamps and simply annotate the spans.
	 *
	 * Check {@link TraceKeys} for examples of most common tag keys
	 */
	void addTag(String key, String value);

	/**
	 * Remove this span from the current thread, but don't stop it yet nor send it for
	 * collection. This is useful if the span object is then passed to another thread for
	 * use with {@link Tracer#continueSpan(Span)}.
	 * <p>
	 * Example of usage:
	 * <pre>{@code
	 *     // Span "A" was present in thread "X". Let's assume that we're in thread "Y" to which span "A" got passed
	 *     Span continuedSpan = tracer.continueSpan(spanA);
	 *     // Now span "A" got continued in thread "Y".
	 *     ... // Some work is done... state of span "A" could get mutated
	 *     Span previouslyStoredSpan = tracer.detach(continuedSpan);
	 *     // Span "A" got removed from the thread Y but it wasn't yet sent for collection.
	 *     // Additional work can be done on span "A" in thread "X" and finally it can get closed and sent for collection
	 *     tracer.close(spanA);
	 * }</pre>
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

	/**
	 * Returns a wrapped {@link Callable} which will be recorded as a span
	 * in the current trace.
	 */
	<V> Callable<V> wrap(Callable<V> callable);

	/**
	 * Returns a wrapped {@link Runnable} which will be recorded as a span
	 * in the current trace.
	 */
	Runnable wrap(Runnable runnable);
}
