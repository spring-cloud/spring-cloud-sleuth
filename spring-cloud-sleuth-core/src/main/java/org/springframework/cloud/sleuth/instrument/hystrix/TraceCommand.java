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

package org.springframework.cloud.sleuth.instrument.hystrix;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.TraceKeys;

import com.netflix.hystrix.HystrixCommand;

/**
 * Abstraction over {@code HystrixCommand} that wraps command execution with Trace setting
 *
 * @see HystrixCommand
 * @see Tracer
 *
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Spencer Gibb
 * @since 1.0.0
 */
public abstract class TraceCommand<R> extends HystrixCommand<R> {

	private static final String HYSTRIX_COMPONENT = "hystrix";

	private final Tracer tracer;
	private final TraceKeys traceKeys;
	private final Span parentSpan;

	protected TraceCommand(Tracer tracer, TraceKeys traceKeys, Setter setter) {
		super(setter);
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.parentSpan = tracer.getCurrentSpan();
	}

	@Override
	protected R run() throws Exception {
		String commandKeyName = getCommandKey().name();
		Span span = startSpan(commandKeyName);
		this.tracer.addTag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, HYSTRIX_COMPONENT);
		this.tracer.addTag(this.traceKeys.getHystrix().getPrefix() +
				this.traceKeys.getHystrix().getCommandKey(), commandKeyName);
		this.tracer.addTag(this.traceKeys.getHystrix().getPrefix() +
				this.traceKeys.getHystrix().getCommandGroup(), getCommandGroup().name());
		this.tracer.addTag(this.traceKeys.getHystrix().getPrefix() +
				this.traceKeys.getHystrix().getThreadPoolKey(), getThreadPoolKey().name());
		try {
			return doRun();
		}
		finally {
			close(span);
		}
	}

	private Span startSpan(String commandKeyName) {
		Span span = this.parentSpan;
		if (span == null) {
			return this.tracer.createSpan(commandKeyName, this.parentSpan);
		}
		return this.tracer.continueSpan(span);
	}

	private void close(Span span) {
		if (this.parentSpan == null) {
			this.tracer.close(span);
		} else {
			this.tracer.detach(span);
		}
	}

	public abstract R doRun() throws Exception;
}
