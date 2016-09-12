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

package org.springframework.cloud.sleuth.reactive;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

import reactor.core.Cancellation;
import reactor.core.scheduler.Scheduler;

/**
 * @author Marcin Grzejszczak
 */
final class TraceScheduler implements Scheduler {

	final Scheduler scheduler;
	final Tracer tracer;

	public TraceScheduler(Scheduler scheduler, Tracer tracer) {
		this.scheduler = scheduler;
		this.tracer = tracer;
	}

	@Override public Cancellation schedule(Runnable task) {
		return scheduler.schedule(new ReactorTraceRunnable(task, tracer));
	}

	@Override public Worker createWorker() {
		return new TraceWorker(scheduler.createWorker(), this.tracer);
	}

	@Override public void start() {
		scheduler.start();
	}

	@Override public void shutdown() {
		scheduler.shutdown();
	}

	final class ReactorTraceRunnable implements Runnable {

		private final Runnable delegate;
		private final Tracer tracer;
		private final Span parent;
		private final boolean cleanup;

		ReactorTraceRunnable(Runnable delegate, Tracer tracer) {
			this.delegate = delegate;
			this.tracer = tracer;
			this.parent = tracer.getCurrentSpan();
			this.cleanup = true;
		}

		ReactorTraceRunnable(Runnable delegate, Tracer tracer, Span parent,
				boolean cleanup) {
			this.delegate = delegate;
			this.tracer = tracer;
			this.parent = parent;
			this.cleanup = cleanup;
		}

		@Override public void run() {
			Span span;
			boolean created;
			if (this.parent == null) {
				span = this.tracer.createSpan("");
				created = true;
			}
			else {
				span = this.tracer.continueSpan(this.parent);
				created = false;
			}
			try {
				delegate.run();
			} finally {
				if (cleanup) {
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

	// It's safe Thread safe
	final class TraceWorker implements Worker {
		final Worker worker;
		final Tracer tracer;
		Span parent;

		public TraceWorker(Worker worker, Tracer tracer) {
			this.worker = worker;
			this.tracer = tracer;
		}

		@Override public Cancellation schedule(Runnable task) {
			this.parent = this.tracer.getCurrentSpan();
			if (this.tracer.isTracing()) {
				this.tracer.detach(this.parent);
			}
			return worker.schedule(new ReactorTraceRunnable(task, tracer, parent, false));
		}

		@Override public void shutdown() {
			try {
				worker.shutdown();
			} finally {
				if (this.parent != null) {
					this.tracer.close(parent);
				}
			}
		}
	}
}
