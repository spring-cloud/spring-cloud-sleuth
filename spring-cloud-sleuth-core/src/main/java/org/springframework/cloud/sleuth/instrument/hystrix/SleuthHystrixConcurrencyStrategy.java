package org.springframework.cloud.sleuth.instrument.hystrix;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.instrument.TraceCallable;

import java.util.concurrent.Callable;

@Slf4j
public class SleuthHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {

	private final TraceManager traceManager;

	public SleuthHystrixConcurrencyStrategy(TraceManager traceManager) {
		this.traceManager = traceManager;
		try {
			HystrixPlugins.getInstance().registerConcurrencyStrategy(this);
		} catch (Exception e) {
			HystrixConcurrencyStrategy concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();
			log.debug("Failed to register Sleuth Hystrix Concurrency Strategy. Will use the current one which is [" + concurrencyStrategy + "]", e);
		}
	}

	@Override
	public <T> Callable<T> wrapCallable(Callable<T> callable) {
		return new TraceCallable<>(traceManager, callable);
	}
}
