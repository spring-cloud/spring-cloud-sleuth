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

package org.springframework.cloud.sleuth.instrument.reactive;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;

import reactor.core.Cancellation;
import reactor.core.scheduler.Scheduler;

/**
 * Trace representation of {@link Scheduler}. Wraps {@link Worker}
 * creation in its trace representation.
 *
 * @author Marcin Grzejszczak
 * @author Stephane Maldini
 * @since 1.0.9
 */
public class TraceScheduler implements Scheduler {

	private final Scheduler delegate;
	private final Tracer tracer;
	private final TraceKeys traceKeys;

	public TraceScheduler(Scheduler delegate, Tracer tracer, TraceKeys traceKeys) {
		this.delegate = delegate;
		this.tracer = tracer;
		this.traceKeys = traceKeys;
	}

	@Override public Cancellation schedule(Runnable task) {
		return this.delegate.schedule(new ReactorTraceRunnable(task, this.tracer,
				this.traceKeys));
	}

	@Override public Worker createWorker() {
		return new TraceWorker(this.delegate.createWorker(), this.tracer, this.traceKeys);
	}

	@Override public void start() {
		this.delegate.start();
	}

	@Override public void shutdown() {
		this.delegate.shutdown();
	}

	/**
	 * Workers are thread safe. What's extremely important is that
	 * the {@link Worker#schedule(Runnable)} method is executed in the
	 * main thread. The delegate's {@code schedule} might be executed in a
	 * separate thread. That's in the main thread we're *detaching* the
	 * span from the current thread. That's because we want the Reactor
	 * to eventually close a span. However closing of a span can take place
	 * in another thread thus we need to clear the main one.
	 */
	private final class TraceWorker implements Worker {
		private final Worker worker;
		private final Tracer tracer;
		private final TraceKeys traceKeys;
		private Span parent;

		private TraceWorker(Worker worker, Tracer tracer, TraceKeys traceKeys) {
			this.worker = worker;
			this.tracer = tracer;
			this.traceKeys = traceKeys;
		}

		@Override public Cancellation schedule(Runnable task) {
			this.parent = this.tracer.getCurrentSpan();
			// we're detaching the span from the current thread
			// since it might be closed in another one
			if (this.tracer.isTracing()) {
				this.tracer.detach(this.parent);
			}
			return this.worker.schedule(new ReactorTraceRunnable(task, this.tracer,
					this.traceKeys, this.parent, false));
		}

		@Override public void shutdown() {
			try {
				this.worker.shutdown();
			} finally {
				if (this.parent != null) {
					this.tracer.close(this.parent);
				}
			}
		}
	}

	/**
	 * Trace representation of {@link Runnable} that either
	 * continues an existing span or creates a new one if there was none.
	 * On completion it either detaches a span if it was continued
	 * or closes it if it was created. Even if the runnable is executed
	 * in the same "main" thread, since we're first detaching the span
	 * it will get continued by this runnable and then reentered to the
	 * tracing context of the current thread.
	 */
	private final class ReactorTraceRunnable implements Runnable {

		private static final String REACTIVE_COMPONENT = "reactive";

		private final Runnable delegate;
		private final Tracer tracer;
		private final TraceKeys traceKeys;
		private final Span parent;
		private final boolean cleanup;

		private ReactorTraceRunnable(Runnable delegate, Tracer tracer,
				TraceKeys traceKeys) {
			this.delegate = delegate;
			this.tracer = tracer;
			this.parent = tracer.getCurrentSpan();
			this.traceKeys = traceKeys;
			this.cleanup = true;
		}

		private ReactorTraceRunnable(Runnable delegate, Tracer tracer,
				TraceKeys traceKeys, Span parent, boolean cleanup) {
			this.delegate = delegate;
			this.tracer = tracer;
			this.traceKeys = traceKeys;
			this.parent = parent;
			this.cleanup = cleanup;
		}

		@Override public void run() {
			Span span;
			boolean created;
			if (this.parent == null) {
				span = this.tracer.createSpan(REACTIVE_COMPONENT);
				created = true;
			}
			else {
				span = this.tracer.continueSpan(this.parent);
				created = false;
			}

			if (!span.tags().containsKey(Span.SPAN_LOCAL_COMPONENT_TAG_NAME)) {
				this.tracer.addTag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, REACTIVE_COMPONENT);
			}
			this.tracer.addTag(this.traceKeys.getAsync().getPrefix()
					+ this.traceKeys.getAsync().getThreadNameKey(), Thread.currentThread().getName());
			try {
				this.delegate.run();
			} finally {
				if (this.cleanup) {
					if (created) {
						this.tracer.close(span);
					}
					else {
						this.tracer.detach(span);
					}
				}
			}
		}
	}
}
