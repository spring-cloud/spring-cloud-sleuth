/*
 * Copyright 2013-2016 the original author or authors.
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

import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;

/**
 * A {@link HystrixConcurrencyStrategy} that wraps a {@link Callable} in a
 * {@link Callable} that either starts a new span or continues one
 * if the tracing was already running before the command was executed.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SleuthHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {

	private static final String HYSTRIX_COMPONENT = "hystrix";
	private static final Log log = LogFactory.getLog(SleuthHystrixConcurrencyStrategy.class);

	private final Tracer tracer;
	private final TraceKeys traceKeys;
	private HystrixConcurrencyStrategy delegate;

	public SleuthHystrixConcurrencyStrategy(Tracer tracer, TraceKeys traceKeys) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		try {
			this.delegate = HystrixPlugins.getInstance().getConcurrencyStrategy();
			HystrixCommandExecutionHook commandExecutionHook = HystrixPlugins
					.getInstance().getCommandExecutionHook();
			HystrixEventNotifier eventNotifier = HystrixPlugins.getInstance()
					.getEventNotifier();
			HystrixMetricsPublisher metricsPublisher = HystrixPlugins.getInstance()
					.getMetricsPublisher();
			HystrixPropertiesStrategy propertiesStrategy = HystrixPlugins.getInstance()
					.getPropertiesStrategy();
			logCurrentStateOfHysrixPlugins(eventNotifier, metricsPublisher,
					propertiesStrategy);
			HystrixPlugins.reset();
			HystrixPlugins.getInstance().registerConcurrencyStrategy(this);
			HystrixPlugins.getInstance().registerCommandExecutionHook(commandExecutionHook);
			HystrixPlugins.getInstance().registerEventNotifier(eventNotifier);
			HystrixPlugins.getInstance().registerMetricsPublisher(metricsPublisher);
			HystrixPlugins.getInstance().registerPropertiesStrategy(propertiesStrategy);
		}
		catch (Exception e) {
			log.error(
					"Failed to register Sleuth Hystrix Concurrency Strategy", e);
		}
	}

	private void logCurrentStateOfHysrixPlugins(HystrixEventNotifier eventNotifier,
			HystrixMetricsPublisher metricsPublisher,
			HystrixPropertiesStrategy propertiesStrategy) {
		log.info("Current Hystrix plugins configuration is ["
				+ "concurrencyStrategy [" + this.delegate + "],"
				+ "eventNotifier [" + eventNotifier + "],"
				+ "metricPublisher [" + metricsPublisher + "],"
				+ "propertiesStrategy [" + propertiesStrategy + "],"
				+ "]");
		log.info("Resetting Hystrix custom strategies and registering Sleuth Hystrix Concurrency Strategy.");
	}

	@Override
	public <T> Callable<T> wrapCallable(Callable<T> callable) {
		Callable<T> wrappedCallable = this.delegate != null ?
				this.delegate.wrapCallable(callable) : callable;
		return new HystrixTraceCallable<>(this.tracer, this.traceKeys, wrappedCallable);
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
				span = this.tracer.createSpan(HYSTRIX_COMPONENT);
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
