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
 * @see CorrelationIdUpdater
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
