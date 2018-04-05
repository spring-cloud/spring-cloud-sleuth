/*
 * Copyright 2013-2018 the original author or authors.
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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.instrument.async.TraceCallable;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixLifecycleForwardingRequestVariable;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class SleuthHystrixConcurrencyStrategyTest {

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(new StrictCurrentTraceContext())
			.spanReporter(this.reporter)
			.build();

	@Before
	@After
	public void setup() {
		HystrixPlugins.reset();
		this.reporter.clear();
	}

	@Test
	public void should_not_override_existing_custom_strategies() {
		HystrixPlugins.getInstance().registerCommandExecutionHook(new MyHystrixCommandExecutionHook());
		HystrixPlugins.getInstance().registerEventNotifier(new MyHystrixEventNotifier());
		HystrixPlugins.getInstance().registerMetricsPublisher(new MyHystrixMetricsPublisher());
		HystrixPlugins.getInstance().registerPropertiesStrategy(new MyHystrixPropertiesStrategy());

		new SleuthHystrixConcurrencyStrategy(this.tracing, new DefaultSpanNamer());

		then(HystrixPlugins
				.getInstance().getCommandExecutionHook()).isExactlyInstanceOf(MyHystrixCommandExecutionHook.class);
		then(HystrixPlugins.getInstance()
				.getEventNotifier()).isExactlyInstanceOf(MyHystrixEventNotifier.class);
		then(HystrixPlugins.getInstance()
				.getMetricsPublisher()).isExactlyInstanceOf(MyHystrixMetricsPublisher.class);
		then(HystrixPlugins.getInstance()
				.getPropertiesStrategy()).isExactlyInstanceOf(MyHystrixPropertiesStrategy.class);
	}

	@Test
	public void should_wrap_delegates_callable_in_trace_callable_when_delegate_is_present()
			throws Exception {
		HystrixPlugins.getInstance().registerConcurrencyStrategy(new MyHystrixConcurrencyStrategy());
		SleuthHystrixConcurrencyStrategy strategy = new SleuthHystrixConcurrencyStrategy(
				this.tracing, new DefaultSpanNamer());

		Callable<String> callable = strategy.wrapCallable(() -> "hello");

		then(callable).isInstanceOf(TraceCallable.class);
		then(callable.call()).isEqualTo("executed_custom_callable");
	}

	@Test
	public void should_wrap_callable_in_trace_callable_when_delegate_is_present()
			throws Exception {
		SleuthHystrixConcurrencyStrategy strategy = new SleuthHystrixConcurrencyStrategy(
				this.tracing, new DefaultSpanNamer());

		Callable<String> callable = strategy.wrapCallable(() -> "hello");

		then(callable).isInstanceOf(TraceCallable.class);
	}

	@Test
	public void should_add_trace_keys_when_span_is_created()
			throws Exception {
		SleuthHystrixConcurrencyStrategy strategy = new SleuthHystrixConcurrencyStrategy(
				this.tracing, new DefaultSpanNamer());
		Callable<String> callable = strategy.wrapCallable(() -> "hello");

		callable.call();

		then(callable).isInstanceOf(TraceCallable.class);
		then(this.reporter.getSpans()).hasSize(1);
	}

	@Test
	public void should_delegate_work_to_custom_hystrix_concurrency_strategy()
			throws Exception {
		HystrixConcurrencyStrategy strategy = Mockito.mock(HystrixConcurrencyStrategy.class);
		HystrixPlugins.getInstance().registerConcurrencyStrategy(strategy);
		SleuthHystrixConcurrencyStrategy sleuthStrategy = new SleuthHystrixConcurrencyStrategy(
				this.tracing, new DefaultSpanNamer());

		sleuthStrategy.wrapCallable(() -> "foo");
		sleuthStrategy.getThreadPool(HystrixThreadPoolKey.Factory.asKey(""), Mockito.mock(
				HystrixThreadPoolProperties.class));
		sleuthStrategy.getThreadPool(HystrixThreadPoolKey.Factory.asKey(""),
				Mockito.mock(HystrixProperty.class), Mockito.mock(HystrixProperty.class),
				Mockito.mock(HystrixProperty.class), TimeUnit.DAYS, Mockito.mock(
						BlockingQueue.class));
		sleuthStrategy.getBlockingQueue(10);
		sleuthStrategy.getRequestVariable(Mockito.mock(
				HystrixLifecycleForwardingRequestVariable.class));

		BDDMockito.then(strategy).should().wrapCallable((Callable) BDDMockito.any());
		BDDMockito.then(strategy).should().getThreadPool(BDDMockito.any(), BDDMockito.any());
		BDDMockito.then(strategy).should().getThreadPool(BDDMockito.any(), BDDMockito.any(),
				BDDMockito.any(), BDDMockito.any(), BDDMockito.any(), BDDMockito.any());
		BDDMockito.then(strategy).should().getThreadPool(BDDMockito.any(), BDDMockito.any(),
				BDDMockito.any(), BDDMockito.any(), BDDMockito.any(), BDDMockito.any());
		BDDMockito.then(strategy).should().getBlockingQueue(10);
		BDDMockito.then(strategy).should().getRequestVariable(BDDMockito.any());
	}

	static class MyHystrixCommandExecutionHook extends HystrixCommandExecutionHook {}
	@SuppressWarnings("unchecked")
	static class MyHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {
		@Override public <T> Callable<T> wrapCallable(Callable<T> callable) {
			return () -> (T) "executed_custom_callable";
		}
	}
	static class MyHystrixEventNotifier extends HystrixEventNotifier {}
	static class MyHystrixMetricsPublisher extends HystrixMetricsPublisher {}
	static class MyHystrixPropertiesStrategy extends HystrixPropertiesStrategy {}
}