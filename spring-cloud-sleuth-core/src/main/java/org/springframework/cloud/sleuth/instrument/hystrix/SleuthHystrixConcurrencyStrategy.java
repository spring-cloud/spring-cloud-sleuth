package org.springframework.cloud.sleuth.instrument.hystrix;

import java.util.concurrent.Callable;

import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.instrument.TraceCallable;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;

public class SleuthHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {

	private final TraceManager traceManager;

	public SleuthHystrixConcurrencyStrategy(TraceManager traceManager) {
		this.traceManager = traceManager;
		HystrixPlugins.getInstance().registerConcurrencyStrategy(this);
	}

	@Override
	public <T> Callable<T> wrapCallable(Callable<T> callable) {
		return new TraceCallable<>(traceManager, callable);
	}
}
