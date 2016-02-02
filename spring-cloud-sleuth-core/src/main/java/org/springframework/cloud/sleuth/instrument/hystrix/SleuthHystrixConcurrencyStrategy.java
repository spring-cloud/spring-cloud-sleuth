package org.springframework.cloud.sleuth.instrument.hystrix;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Callable;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceCallable;

public class SleuthHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {

	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Tracer tracer;

	public SleuthHystrixConcurrencyStrategy(Tracer tracer) {
		this.tracer = tracer;
		try {
			HystrixPlugins.getInstance().registerConcurrencyStrategy(this);
		} catch (Exception e) {
			HystrixConcurrencyStrategy concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();
			log.debug("Failed to register Sleuth Hystrix Concurrency Strategy. Will use the current one which is [" + concurrencyStrategy + "]", e);
		}
	}

	@Override
	public <T> Callable<T> wrapCallable(Callable<T> callable) {
		return new TraceCallable<>(this.tracer, callable);
	}
}
