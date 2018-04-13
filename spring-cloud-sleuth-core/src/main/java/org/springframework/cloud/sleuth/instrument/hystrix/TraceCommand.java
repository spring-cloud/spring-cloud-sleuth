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

package org.springframework.cloud.sleuth.instrument.hystrix;

import brave.Span;
import brave.Tracer;
import com.netflix.hystrix.HystrixCommand;

/**
 * Abstraction over {@code HystrixCommand} that wraps command execution with Trace setting
 *
 * @see HystrixCommand
 * @see Tracer
 *
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @since 1.0.0
 */
public abstract class TraceCommand<R> extends HystrixCommand<R> {

	private static final String COMMAND_KEY = "commandKey";
	private static final String COMMAND_GROUP_KEY = "commandGroup";
	private static final String THREAD_POOL_KEY = "threadPoolKey";

	private final Tracer tracer;
	private final Span span;

	protected TraceCommand(Tracer tracer, Setter setter) {
		super(setter);
		this.tracer = tracer;
		this.span = this.tracer.nextSpan();
	}

	@Override
	protected R run() throws Exception {
		String commandKeyName = getCommandKey().name();
		Span span = this.span.name(commandKeyName);
		span.tag(COMMAND_KEY, commandKeyName);
		span.tag(COMMAND_GROUP_KEY, getCommandGroup().name());
		span.tag(THREAD_POOL_KEY, getThreadPoolKey().name());
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			return doRun();
		}
		finally {
			span.finish();
		}
	}

	public abstract R doRun() throws Exception;
}
