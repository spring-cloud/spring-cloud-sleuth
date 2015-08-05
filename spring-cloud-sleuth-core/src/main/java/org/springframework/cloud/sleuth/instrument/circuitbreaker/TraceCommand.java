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

package org.springframework.cloud.sleuth.instrument.circuitbreaker;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;

/**
 * Abstraction over {@code HystrixCommand} that wraps command execution with Trace setting
 *
 * @see HystrixCommand
 * @see Trace
 *
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Spencer Gibb
 */
public abstract class TraceCommand<R> extends HystrixCommand<R> {

	private Trace trace;

	protected TraceCommand(Trace trace, HystrixCommandGroupKey group) {
		super(group);
		this.trace = trace;
	}

	protected TraceCommand(Trace trace, HystrixCommandGroupKey group, HystrixThreadPoolKey threadPool) {
		super(group, threadPool);
		this.trace = trace;
	}

	protected TraceCommand(Trace trace, HystrixCommandGroupKey group, int executionIsolationThreadTimeoutInMilliseconds) {
		super(group, executionIsolationThreadTimeoutInMilliseconds);
		this.trace = trace;
	}

	protected TraceCommand(Trace trace, HystrixCommandGroupKey group, HystrixThreadPoolKey threadPool, int executionIsolationThreadTimeoutInMilliseconds) {
		super(group, threadPool, executionIsolationThreadTimeoutInMilliseconds);
		this.trace = trace;
	}

	protected TraceCommand(Trace trace, Setter setter) {
		super(setter);
		this.trace = trace;
	}

	@Override
	protected R run() throws Exception {
		TraceScope scope = trace.startSpan(getCommandKey().name());
		try {
			return doRun();
		} finally {
			scope.close();
		}
	}

	public abstract R doRun() throws Exception;
}
