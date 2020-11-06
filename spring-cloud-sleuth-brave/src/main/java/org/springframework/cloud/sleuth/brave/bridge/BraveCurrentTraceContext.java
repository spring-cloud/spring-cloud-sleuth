/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.bridge;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.TraceContext;

/**
 * Brave implementation of a {@link CurrentTraceContext}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class BraveCurrentTraceContext implements CurrentTraceContext {

	final brave.propagation.CurrentTraceContext delegate;

	public BraveCurrentTraceContext(brave.propagation.CurrentTraceContext delegate) {
		this.delegate = delegate;
	}

	@Override
	public TraceContext context() {
		brave.propagation.TraceContext context = this.delegate.get();
		if (context == null) {
			return null;
		}
		return new BraveTraceContext(context);
	}

	@Override
	public Scope newScope(TraceContext context) {
		return new BraveScope(this.delegate.newScope(BraveTraceContext.toBrave(context)));
	}

	@Override
	public Scope maybeScope(TraceContext context) {
		return new BraveScope(this.delegate.maybeScope(BraveTraceContext.toBrave(context)));
	}

	@Override
	public <C> Callable<C> wrap(Callable<C> task) {
		return this.delegate.wrap(task);
	}

	@Override
	public Runnable wrap(Runnable task) {
		return this.delegate.wrap(task);
	}

	@Override
	public Executor wrap(Executor delegate) {
		return this.delegate.executor(delegate);
	}

	@Override
	public ExecutorService wrap(ExecutorService delegate) {
		return this.delegate.executorService(delegate);
	}

	public static brave.propagation.CurrentTraceContext toBrave(CurrentTraceContext context) {
		return ((BraveCurrentTraceContext) context).delegate;
	}

	public static CurrentTraceContext fromBrave(brave.propagation.CurrentTraceContext context) {
		return new BraveCurrentTraceContext(context);
	}

}

class BraveScope implements CurrentTraceContext.Scope {

	private final brave.propagation.CurrentTraceContext.Scope delegate;

	BraveScope(brave.propagation.CurrentTraceContext.Scope delegate) {
		this.delegate = delegate;
	}

	@Override
	public void close() {
		this.delegate.close();
	}

}
