package org.springframework.cloud.sleuth.instrument.hystrix;

import javax.annotation.PreDestroy;
import java.util.concurrent.Callable;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.Tracer;

@Slf4j
public class SleuthHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {

	private static final String HYSTRIX_PROTOCOL = "hystrix";

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
				span = this.tracer.startTrace(new SpanName(HYSTRIX_PROTOCOL,
						Thread.currentThread().getName()));
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
