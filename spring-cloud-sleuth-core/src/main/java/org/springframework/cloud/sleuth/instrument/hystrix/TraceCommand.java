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

package org.springframework.cloud.sleuth.instrument.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;

/**
 * Abstraction over {@code HystrixCommand} that wraps command execution with Trace setting
 *
 * @see HystrixCommand
 * @see Tracer
 *
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Spencer Gibb
 */
public abstract class TraceCommand<R> extends HystrixCommand<R> {

	private final Tracer tracer;
	private final Span parentSpan;

	protected TraceCommand(Tracer tracer, HystrixCommandGroupKey group) {
		super(group);
		this.tracer = tracer;
		this.parentSpan = tracer.getCurrentSpan();
	}

	protected TraceCommand(Tracer tracer, HystrixCommandGroupKey group, HystrixThreadPoolKey threadPool) {
		super(group, threadPool);
		this.tracer = tracer;
		this.parentSpan = tracer.getCurrentSpan();
	}

	protected TraceCommand(Tracer tracer, HystrixCommandGroupKey group, int executionIsolationThreadTimeoutInMilliseconds) {
		super(group, executionIsolationThreadTimeoutInMilliseconds);
		this.tracer = tracer;
		this.parentSpan = tracer.getCurrentSpan();
	}

	protected TraceCommand(Tracer tracer, HystrixCommandGroupKey group, HystrixThreadPoolKey threadPool, int executionIsolationThreadTimeoutInMilliseconds) {
		super(group, threadPool, executionIsolationThreadTimeoutInMilliseconds);
		this.tracer = tracer;
		this.parentSpan = tracer.getCurrentSpan();
	}

	protected TraceCommand(Tracer tracer, Setter setter) {
		super(setter);
		this.tracer = tracer;
		this.parentSpan = tracer.getCurrentSpan();
	}

	@Override
	protected R run() throws Exception {
		enforceThatHystrixThreadIsNotPollutedByPreviousTraces();
		Trace trace = this.tracer.joinTrace(getCommandKey().name(), parentSpan);
		try {
			return doRun();
		} finally {
			this.tracer.close(trace);
		}
	}

	// TODO: Do more analysis why this is not removed properly
	private void enforceThatHystrixThreadIsNotPollutedByPreviousTraces() {
		TraceContextHolder.removeCurrentTrace();
	}

	public abstract R doRun() throws Exception;
}
