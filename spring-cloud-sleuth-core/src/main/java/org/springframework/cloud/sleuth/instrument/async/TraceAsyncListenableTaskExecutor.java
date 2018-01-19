/*
 * Copyright 2013-2018 the original author or authors.
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
import java.util.concurrent.Future;

import brave.Tracing;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * AsyncListenableTaskExecutor that wraps all Runnable / Callable tasks into
 * their trace related representation
 *
 * @since 1.0.0
 *
 * @see brave.propagation.CurrentTraceContext#wrap(Runnable)
 * @see brave.propagation.CurrentTraceContext#wrap(Callable)
 */
public class TraceAsyncListenableTaskExecutor implements AsyncListenableTaskExecutor {

	private final AsyncListenableTaskExecutor delegate;
	private final Tracing tracing;

	TraceAsyncListenableTaskExecutor(AsyncListenableTaskExecutor delegate,
			Tracing tracing) {
		this.delegate = delegate;
		this.tracing = tracing;
	}

	@Override
	public ListenableFuture<?> submitListenable(Runnable task) {
		return this.delegate.submitListenable(this.tracing.currentTraceContext().wrap(task));
	}

	@Override
	public <T> ListenableFuture<T> submitListenable(Callable<T> task) {
		return this.delegate.submitListenable(this.tracing.currentTraceContext().wrap(task));
	}

	@Override
	public void execute(Runnable task, long startTimeout) {
		this.delegate.execute(this.tracing.currentTraceContext().wrap(task), startTimeout);
	}

	@Override
	public Future<?> submit(Runnable task) {
		return this.delegate.submit(this.tracing.currentTraceContext().wrap(task));
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return this.delegate.submit(this.tracing.currentTraceContext().wrap(task));
	}

	@Override
	public void execute(Runnable task) {
		this.delegate.execute(this.tracing.currentTraceContext().wrap(task));
	}

}