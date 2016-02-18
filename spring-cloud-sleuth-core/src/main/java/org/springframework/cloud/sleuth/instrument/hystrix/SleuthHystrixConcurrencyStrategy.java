package org.springframework.cloud.sleuth.instrument.hystrix;

import javax.annotation.PreDestroy;
import java.util.concurrent.Callable;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;

public class SleuthHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {

	private static final String HYSTRIX_COMPONENT = "hystrix";
	private static final Log log = LogFactory.getLog(SleuthHystrixConcurrencyStrategy.class);

	private final Tracer tracer;
	private final TraceKeys traceKeys;

	public SleuthHystrixConcurrencyStrategy(Tracer tracer, TraceKeys traceKeys) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		try {
			HystrixPlugins.getInstance().registerConcurrencyStrategy(this);
		}
		catch (Exception e) {
			HystrixConcurrencyStrategy concurrencyStrategy = HystrixPlugins.getInstance()
					.getConcurrencyStrategy();
			log.debug(
					"Failed to register Sleuth Hystrix Concurrency Strategy. Will use the current one which is ["
							+ concurrencyStrategy + "]",
					e);
		}
	}

	@PreDestroy
	public void close() {
		HystrixPlugins.reset();
	}

	@Override
	public <T> Callable<T> wrapCallable(Callable<T> callable) {
		return new HystrixTraceCallable<T>(this.tracer, this.traceKeys, callable);
	}

	private static class HystrixTraceCallable<S> implements Callable<S> {

		private Tracer tracer;
		private TraceKeys traceKeys;
		private Callable<S> callable;
		private Span parent;

		public HystrixTraceCallable(Tracer tracer, TraceKeys traceKeys, Callable<S> callable) {
			this.tracer = tracer;
			this.traceKeys = traceKeys;
			this.callable = callable;
			this.parent = tracer.getCurrentSpan();
		}

		@Override
		public S call() throws Exception {
			Span span = this.parent;
			boolean created = false;
			if (span != null) {
				span = this.tracer.continueSpan(span);
			}
			else {
				span = this.tracer.startTrace(HYSTRIX_COMPONENT);
				this.tracer.addTag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, HYSTRIX_COMPONENT);
				this.tracer.addTag(this.traceKeys.getAsync().getPrefix() +
						this.traceKeys.getAsync().getThreadNameKey(), Thread.currentThread().getName());
				created = true;
			}
			try {
				return this.callable.call();
			}
			finally {
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
