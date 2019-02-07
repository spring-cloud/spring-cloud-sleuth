/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.hystrix;

import java.util.concurrent.atomic.AtomicReference;

import brave.Span;
import brave.Tracer;
import com.netflix.hystrix.HystrixCommand;

/**
 * Abstraction over {@code HystrixCommand} that wraps command execution with Trace
 * setting.
 *
 * @param <R> - return type of Hystrix Command
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @since 1.0.0
 * @see HystrixCommand
 * @see Tracer
 */
public abstract class TraceCommand<R> extends HystrixCommand<R> {

	private static final String COMMAND_KEY = "commandKey";

	private static final String COMMAND_GROUP_KEY = "commandGroup";

	private static final String THREAD_POOL_KEY = "threadPoolKey";

	private static final String FALLBACK_METHOD_NAME_KEY = "fallbackMethodName";

	private final Tracer tracer;

	private final AtomicReference<Span> span;

	protected TraceCommand(Tracer tracer, Setter setter) {
		super(setter);
		this.tracer = tracer;
		this.span = new AtomicReference<>(this.tracer.nextSpan());
	}

	@Override
	protected R run() throws Exception {
		String commandKeyName = getCommandKey().name();
		Span span = this.span.get().name(commandKeyName);
		span.tag(COMMAND_KEY, commandKeyName);
		span.tag(COMMAND_GROUP_KEY, getCommandGroup().name());
		span.tag(THREAD_POOL_KEY, getThreadPoolKey().name());
		Throwable throwable = null;
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			return doRun();
		}
		catch (Throwable t) {
			throwable = t;
			throw t;
		}
		finally {
			if (throwable == null) {
				span.finish();
				this.span.set(null);
			}
			// else there will be fallback
		}
	}

	public abstract R doRun() throws Exception;

	@Override
	protected R getFallback() {
		Span span = this.span.get();
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			span.tag(FALLBACK_METHOD_NAME_KEY, getFallbackMethodName());
			return doGetFallback();
		}
		finally {
			span.finish();
			this.span.set(null);
		}
	}

	public R doGetFallback() {
		return super.getFallback();
	}

}
