package org.springframework.cloud.sleuth.instrument.hystrix;

import java.util.concurrent.Callable;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;

public class SleuthHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {

	private static final String HYSTRIX_COMPONENT = "hystrix";
	private static final Logger log = org.slf4j.LoggerFactory
			.getLogger(SleuthHystrixConcurrencyStrategy.class);

	private final Tracer tracer;

	public SleuthHystrixConcurrencyStrategy(Tracer tracer) {
		this.tracer = tracer;
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
		return new HystrixTraceCallable<T>(this.tracer, callable);
	}

	private static class HystrixTraceCallable<S> implements Callable<S> {

		private Tracer tracer;
		private Callable<S> callable;
		private Span parent;

		public HystrixTraceCallable(Tracer tracer, Callable<S> callable) {
			this.tracer = tracer;
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
				span = this.tracer.startTrace(HYSTRIX_COMPONENT +
						":" + Thread.currentThread().getName());
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
