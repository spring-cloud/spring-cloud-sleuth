/*
 * Copyright 2015 the original author or authors.
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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;

/**
 * A decorator class for {@link ExecutorService} to support tracing in Executors
 * @author Gaurav Rai Mazra
 *
 */
public class TraceableExecutorService implements ExecutorService {
	final ExecutorService delegate;
	final Tracer tracer;
	private final String spanName;
	final TraceKeys traceKeys;
	final SpanNamer spanNamer;

	public TraceableExecutorService(final ExecutorService delegate, final Tracer tracer,
			TraceKeys traceKeys, SpanNamer spanNamer) {
		this(delegate, tracer, traceKeys, spanNamer, null);
	}

	public TraceableExecutorService(final ExecutorService delegate, final Tracer tracer,
			TraceKeys traceKeys, SpanNamer spanNamer, String spanName) {
		this.delegate = delegate;
		this.tracer = tracer;
		this.spanName = spanName;
		this.traceKeys = traceKeys;
		this.spanNamer = spanNamer;
	}

	@Override
	public void execute(Runnable command) {
		final Runnable r = new LocalComponentTraceRunnable(this.tracer, this.traceKeys,
				this.spanNamer, command, this.spanName);
		this.delegate.execute(r);
	}

	@Override
	public void shutdown() {
		this.delegate.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		return this.delegate.shutdownNow();
	}

	@Override
	public boolean isShutdown() {
		return this.delegate.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return this.delegate.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return this.delegate.awaitTermination(timeout, unit);
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		Callable<T> c = new LocalComponentTraceCallable<>(this.tracer, this.traceKeys,
				this.spanNamer, this.spanName, task);
		return this.delegate.submit(c);
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		Runnable r = new LocalComponentTraceRunnable(this.tracer, this.traceKeys,
				this.spanNamer, task, this.spanName);
		return this.delegate.submit(r, result);
	}

	@Override
	public Future<?> submit(Runnable task) {
		Runnable r = new LocalComponentTraceRunnable(this.tracer, this.traceKeys,
				this.spanNamer, task, this.spanName);
		return this.delegate.submit(r);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		return this.delegate.invokeAll(tasks);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException {
		return this.delegate.invokeAll(tasks, timeout, unit);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		return this.delegate.invokeAny(tasks);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		return this.delegate.invokeAny(tasks, timeout, unit);
	}

}
