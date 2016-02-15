package org.springframework.cloud.sleuth.instrument.hystrix;

import javax.annotation.PreDestroy;
import java.util.concurrent.Callable;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import org.slf4j.Logger;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanHolder;
import org.springframework.cloud.sleuth.SpanStarter;
import org.springframework.cloud.sleuth.Tracer;

public class SleuthHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {

	private static final String HYSTRIX_COMPONENT = "hystrix";
	private static final Logger log = org.slf4j.LoggerFactory
			.getLogger(SleuthHystrixConcurrencyStrategy.class);

	private final Tracer tracer;
	private final SpanStarter spanStarter;

	public SleuthHystrixConcurrencyStrategy(Tracer tracer) {
		this.tracer = tracer;
		this.spanStarter = new SpanStarter(tracer);
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
		return new HystrixTraceCallable<T>(this.tracer, this.spanStarter, callable);
	}

	private static class HystrixTraceCallable<S> implements Callable<S> {

		private final SpanStarter spanStarter;
		private final Callable<S> callable;
		private final Span parent;

		public HystrixTraceCallable(Tracer tracer, SpanStarter spanStarter, Callable<S> callable) {
			this.spanStarter = spanStarter;
			this.callable = callable;
			this.parent = tracer.getCurrentSpan();
		}

		@Override
		public S call() throws Exception {
			SpanHolder span = this.spanStarter.startOrContinueSpan(
					HYSTRIX_COMPONENT + ":" + Thread.currentThread().getName(),
					this.parent);
			try {
				return this.callable.call();
			}
			finally {
				this.spanStarter.closeOrDetach(span);
			}
		}

	}
}
