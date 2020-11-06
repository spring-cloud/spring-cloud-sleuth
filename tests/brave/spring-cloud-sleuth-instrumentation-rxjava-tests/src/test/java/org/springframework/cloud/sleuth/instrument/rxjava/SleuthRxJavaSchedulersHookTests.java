/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.rxjava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rx.functions.Action0;
import rx.plugins.RxJavaErrorHandler;
import rx.plugins.RxJavaObservableExecutionHook;
import rx.plugins.RxJavaPlugins;
import rx.plugins.RxJavaSchedulersHook;

import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.brave.bridge.BraveBaggageManager;
import org.springframework.cloud.sleuth.brave.bridge.BraveTracer;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Shivang Shah
 */
public class SleuthRxJavaSchedulersHookTests {

	private static StringBuilder caller;

	List<String> threadsToIgnore = new ArrayList<>();

	StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();

	TestSpanHandler spans = new TestSpanHandler();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(this.currentTraceContext).addSpanHandler(this.spans)
			.build();

	BraveBaggageManager braveBaggageManager = new BraveBaggageManager();

	Tracer tracer = BraveTracer.fromBrave(this.tracing.tracer(), this.braveBaggageManager);

	@AfterEach
	public void clean() {
		this.tracing.close();
		this.spans.clear();
		this.currentTraceContext.close();
	}

	@BeforeEach
	@AfterEach
	public void setup() {
		RxJavaPlugins.getInstance().reset();
		caller = new StringBuilder();
	}

	@Test
	public void should_not_override_existing_custom_hooks() {
		RxJavaPlugins.getInstance().registerErrorHandler(new MyRxJavaErrorHandler());
		RxJavaPlugins.getInstance().registerObservableExecutionHook(new MyRxJavaObservableExecutionHook());

		new SleuthRxJavaSchedulersHook(this.tracer, this.threadsToIgnore);

		then(RxJavaPlugins.getInstance().getErrorHandler()).isExactlyInstanceOf(MyRxJavaErrorHandler.class);
		then(RxJavaPlugins.getInstance().getObservableExecutionHook())
				.isExactlyInstanceOf(MyRxJavaObservableExecutionHook.class);
	}

	@Test
	public void should_wrap_delegates_action_in_wrapped_action_when_delegate_is_present_on_schedule() {
		RxJavaPlugins.getInstance().registerSchedulersHook(new MyRxJavaSchedulersHook());
		SleuthRxJavaSchedulersHook schedulersHook = new SleuthRxJavaSchedulersHook(this.tracer, this.threadsToIgnore);
		Action0 action = schedulersHook.onSchedule(() -> {
			caller = new StringBuilder("hello");
		});

		action.call();

		then(action).isInstanceOf(SleuthRxJavaSchedulersHook.TraceAction.class);
		then(caller.toString()).isEqualTo("called_from_schedulers_hook");
		then(this.spans).isNotEmpty();
		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void should_not_create_a_span_when_current_thread_should_be_ignored()
			throws ExecutionException, InterruptedException {
		String threadNameToIgnore = "^MyCustomThread.*$";
		RxJavaPlugins.getInstance().registerSchedulersHook(new MyRxJavaSchedulersHook());
		SleuthRxJavaSchedulersHook schedulersHook = new SleuthRxJavaSchedulersHook(this.tracer,
				Collections.singletonList(threadNameToIgnore));
		Future<Void> hello = executorService().submit((Callable<Void>) () -> {
			Action0 action = schedulersHook.onSchedule(() -> {
				caller = new StringBuilder("hello");
			});
			action.call();
			return null;
		});

		hello.get();

		then(this.spans).isEmpty();
		then(this.tracer.currentSpan()).isNull();
	}

	private ExecutorService executorService() {
		ThreadFactory threadFactory = r -> {
			Thread thread = new Thread(r);
			thread.setName("MyCustomThread10");
			return thread;
		};
		return Executors.newSingleThreadExecutor(threadFactory);
	}

	static class MyRxJavaObservableExecutionHook extends RxJavaObservableExecutionHook {

	}

	static class MyRxJavaSchedulersHook extends RxJavaSchedulersHook {

		@Override
		public Action0 onSchedule(Action0 action) {
			return () -> {
				caller = new StringBuilder("called_from_schedulers_hook");
			};
		}

	}

	static class MyRxJavaErrorHandler extends RxJavaErrorHandler {

	}

}
