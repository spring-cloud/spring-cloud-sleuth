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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class SleuthHystrixConcurrencyStrategyTest {

	@Mock Tracer tracer;
	TraceKeys traceKeys = new TraceKeys();

	@Before
	@After
	public void setup() {
		HystrixPlugins.reset();
	}

	@Test
	public void should_not_override_existing_custom_strategies() {
		HystrixPlugins.getInstance().registerCommandExecutionHook(new MyHystrixCommandExecutionHook());
		HystrixPlugins.getInstance().registerEventNotifier(new MyHystrixEventNotifier());
		HystrixPlugins.getInstance().registerMetricsPublisher(new MyHystrixMetricsPublisher());
		HystrixPlugins.getInstance().registerPropertiesStrategy(new MyHystrixPropertiesStrategy());

		new SleuthHystrixConcurrencyStrategy(this.tracer, this.traceKeys);

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
				this.tracer, this.traceKeys);

		Callable<String> callable = strategy.wrapCallable(() -> "hello");

		then(callable).isInstanceOf(SleuthHystrixConcurrencyStrategy.HystrixTraceCallable.class);
		then(callable.call()).isEqualTo("executed_custom_callable");
	}

	@Test
	public void should_wrap_callable_in_trace_callable_when_delegate_is_present()
			throws Exception {
		SleuthHystrixConcurrencyStrategy strategy = new SleuthHystrixConcurrencyStrategy(
				this.tracer, this.traceKeys);

		Callable<String> callable = strategy.wrapCallable(() -> "hello");

		then(callable).isInstanceOf(SleuthHystrixConcurrencyStrategy.HystrixTraceCallable.class);
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