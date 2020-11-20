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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aopalliance.aop.Advice;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;

/**
 * @author Marcin Grzejszczak
 * @author Denys Ivano
 * @author Vladislav Fefelov
 */
@ExtendWith(MockitoExtension.class)
public class ExecutorInstrumentorTests {

	@Mock(lenient = true)
	BeanFactory beanFactory;

	@Test
	public void should_create_a_cglib_proxy_by_default() throws Exception {
		Object o = new ExecutorInstrumentor(Collections::emptyList, beanFactory).instrument(new Foo(), "foo");

		BDDAssertions.then(o).isInstanceOf(Foo.class);
		BDDAssertions.then(AopUtils.isCglibProxy(o)).isTrue();
	}

	@Test
	public void should_fallback_to_sleuth_implementation_when_cglib_cannot_be_created_for_executor() throws Exception {
		ExecutorService service = Executors.newSingleThreadExecutor();

		Object o = new ExecutorInstrumentor(Collections::emptyList, beanFactory).instrument(service, "foo");

		BDDAssertions.then(o).isInstanceOf(TraceableExecutorService.class);
		service.shutdown();
	}

	@Test
	public void should_fallback_to_sleuth_implementation_when_cglib_cannot_be_created_for_scheduled_executor()
			throws Exception {
		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();

		Object o = new ExecutorInstrumentor(Collections::emptyList, beanFactory).instrument(service, "foo");

		BDDAssertions.then(o).isInstanceOf(TraceableScheduledExecutorService.class);
		service.shutdown();
	}

	@Test
	public void should_do_nothing_when_bean_is_already_lazy_trace_async_task_executor() throws Exception {
		LazyTraceAsyncTaskExecutor service = BDDMockito.mock(LazyTraceAsyncTaskExecutor.class);

		Object o = new ExecutorInstrumentor(Collections::emptyList, beanFactory).instrument(service, "foo");

		BDDAssertions.then(o).isSameAs(service);
	}

	@Test
	public void should_do_nothing_when_bean_is_already_lazy_trace_executor() throws Exception {
		LazyTraceExecutor service = BDDMockito.mock(LazyTraceExecutor.class);

		Object o = new ExecutorInstrumentor(Collections::emptyList, beanFactory).instrument(service, "foo");

		BDDAssertions.then(o).isSameAs(service);
	}

	@Test
	public void should_do_nothing_when_bean_is_already_lazy_thread_pool_task_executor() throws Exception {
		LazyTraceThreadPoolTaskExecutor service = BDDMockito.mock(LazyTraceThreadPoolTaskExecutor.class);

		Object o = new ExecutorInstrumentor(Collections::emptyList, beanFactory).instrument(service, "foo");

		BDDAssertions.then(o).isSameAs(service);
	}

	@Test
	public void should_do_nothing_when_bean_is_already_traceable_executor() throws Exception {
		TraceableExecutorService service = BDDMockito.mock(TraceableExecutorService.class);

		Object o = new ExecutorInstrumentor(Collections::emptyList, beanFactory).instrument(service, "foo");

		BDDAssertions.then(o).isSameAs(service);
	}

	@Test
	public void should_do_nothing_when_bean_is_already_traceable_scheduled_executor() throws Exception {
		TraceableScheduledExecutorService service = BDDMockito.mock(TraceableScheduledExecutorService.class);

		Object o = new ExecutorInstrumentor(Collections::emptyList, beanFactory).instrument(service, "foo");

		BDDAssertions.then(o).isSameAs(service);
	}

	@Test
	public void should_fallback_to_default_implementation_when_exception_thrown() throws Exception {
		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		ExecutorInstrumentor bpp = new ExecutorInstrumentor(Collections::emptyList, beanFactory) {

			@Override
			Object createProxy(Object bean, boolean cglibProxy, Advice advice) {
				throw new AopConfigException("foo");
			}

		};

		Object wrappedService = bpp.instrument(service, "foo");

		BDDAssertions.then(wrappedService).isInstanceOf(TraceableScheduledExecutorService.class);
		service.shutdown();
	}

	@Test
	public void should_create_a_cglib_proxy_by_default_for_ThreadPoolTaskExecutor() throws Exception {
		Object o = new ExecutorInstrumentor(Collections::emptyList, beanFactory)
				.instrument(new FooThreadPoolTaskExecutor(), "foo");

		BDDAssertions.then(o).isInstanceOf(FooThreadPoolTaskExecutor.class);
		BDDAssertions.then(AopUtils.isCglibProxy(o)).isTrue();
	}

	@Test
	public void should_throw_exception_when_it_is_not_possible_to_create_any_proxy_for_ThreadPoolTaskExecutor()
			throws Exception {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		ExecutorInstrumentor bpp = new ExecutorInstrumentor(Collections::emptyList, beanFactory) {
			@Override
			Object createThreadPoolTaskExecutorProxy(Object bean, boolean cglibProxy, ThreadPoolTaskExecutor executor,
					String beanName) {
				throw new AopConfigException("foo");
			}
		};

		BDDAssertions.thenThrownBy(() -> bpp.instrument(taskExecutor, "foo")).isInstanceOf(AopConfigException.class)
				.hasMessage("foo");
	}

	@Test
	public void should_fallback_to_sleuth_impl_when_it_is_not_possible_to_create_any_proxy_for_ExecutorService()
			throws Exception {
		ExecutorService service = BDDMockito.mock(ExecutorService.class);
		ExecutorInstrumentor bpp = new ExecutorInstrumentor(Collections::emptyList, beanFactory) {
			@Override
			Object getObject(ProxyFactoryBean factory) {
				throw new AopConfigException("foo");
			}
		};

		Object o = bpp.instrument(service, "foo");

		BDDAssertions.then(o).isInstanceOf(TraceableExecutorService.class);
	}

	@Test
	public void should_throw_exception_from_the_wrapped_object() throws Exception {
		ExecutorService service = exceptionThrowingExecutorService();
		ExecutorInstrumentor bpp = new ExecutorInstrumentor(Collections::emptyList, beanFactory);

		ExecutorService o = (ExecutorService) bpp.instrument(service, "foo");

		BDDAssertions.thenThrownBy(() -> o.submit((Callable<Object>) () -> "hello")).hasMessage("foo")
				.isInstanceOf(IllegalStateException.class);
	}

	private ExecutorService exceptionThrowingExecutorService() {
		return new ExecutorService() {
			@Override
			public void execute(Runnable command) {

			}

			@Override
			public void shutdown() {

			}

			@Override
			public List<Runnable> shutdownNow() {
				return null;
			}

			@Override
			public boolean isShutdown() {
				return false;
			}

			@Override
			public boolean isTerminated() {
				return false;
			}

			@Override
			public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
				return false;
			}

			@Override
			public <T> Future<T> submit(Callable<T> task) {
				throw new IllegalStateException("foo");
			}

			@Override
			public <T> Future<T> submit(Runnable task, T result) {
				return null;
			}

			@Override
			public Future<?> submit(Runnable task) {
				return null;
			}

			@Override
			public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
				return null;
			}

			@Override
			public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
					throws InterruptedException {
				return null;
			}

			@Override
			public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
					throws InterruptedException, ExecutionException {
				return null;
			}

			@Override
			public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
					throws InterruptedException, ExecutionException, TimeoutException {
				return null;
			}
		};
	}

	@Test
	public void should_use_jdk_proxy_when_executor_has_final_methods() {
		ExecutorInstrumentor beanPostProcessor = new ExecutorInstrumentor(Collections::emptyList, beanFactory);
		Executor executor = Runnable::run;
		Executor wrappedExecutor = (Executor) beanPostProcessor.instrument(executor, "executor");

		BDDAssertions.then(AopUtils.isJdkDynamicProxy(wrappedExecutor)).isTrue();
		BDDAssertions.then(AopUtils.isCglibProxy(wrappedExecutor)).isFalse();

		AtomicBoolean wasCalled = new AtomicBoolean(false);
		wrappedExecutor.execute(() -> {
			wasCalled.set(true);
		});
		BDDAssertions.then(wasCalled).isTrue();
	}

	@Test
	public void should_use_cglib_proxy_when_an_executor_has_a_final_package_protected_method() {
		ExecutorInstrumentor beanPostProcessor = new ExecutorInstrumentor(Collections::emptyList, beanFactory);
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(10);
		ScheduledThreadPoolExecutor wrappedExecutor = (ScheduledThreadPoolExecutor) beanPostProcessor
				.instrument(executor, "executor");

		BDDAssertions.then(AopUtils.isCglibProxy(wrappedExecutor)).isTrue();

		AtomicBoolean wasCalled = new AtomicBoolean(false);
		wrappedExecutor.execute(() -> wasCalled.set(true));
		Awaitility.await().untilAsserted(() -> BDDAssertions.then(wasCalled).isTrue());
	}

	@Test
	public void should_use_jdk_proxy_when_executor_service_has_final_methods() throws Exception {
		ExecutorInstrumentor beanPostProcessor = new ExecutorInstrumentor(Collections::emptyList, beanFactory);
		ExecutorService executorService = new DelegatingSecurityContextExecutorService(
				Executors.newSingleThreadExecutor());
		ExecutorService wrappedExecutor = (ExecutorService) beanPostProcessor.instrument(executorService,
				"executorService");

		BDDAssertions.then(AopUtils.isJdkDynamicProxy(wrappedExecutor)).isTrue();
		BDDAssertions.then(AopUtils.isCglibProxy(wrappedExecutor)).isFalse();
		BDDAssertions.then(wrappedExecutor.submit(() -> "done").get()).isEqualTo("done");
		wrappedExecutor.shutdownNow();
	}

	@Test
	public void should_use_jdk_proxy_when_async_task_executor_has_final_methods() throws Exception {
		ExecutorInstrumentor beanPostProcessor = new ExecutorInstrumentor(Collections::emptyList, beanFactory);

		AsyncTaskExecutor wrappedExecutor = (AsyncTaskExecutor) beanPostProcessor.instrument(new DirectTaskExecutor(),
				"taskExecutor");

		BDDAssertions.then(AopUtils.isJdkDynamicProxy(wrappedExecutor)).isTrue();
		BDDAssertions.then(AopUtils.isCglibProxy(wrappedExecutor)).isFalse();
		BDDAssertions.then(wrappedExecutor.submit(() -> "done").get()).isEqualTo("done");
	}

	@Test
	public void should_fallback_to_sleuth_impl_when_thread_pool_task_executor_has_final_methods() {
		ExecutorInstrumentor postProcessor = new ExecutorInstrumentor(Collections::emptyList, beanFactory);
		ThreadPoolTaskExecutor threadPoolTaskExecutor = new PoolTaskExecutor();

		ThreadPoolTaskExecutor wrappedTaskExecutor = (ThreadPoolTaskExecutor) postProcessor
				.instrument(threadPoolTaskExecutor, "threadPoolTaskExecutor");

		BDDAssertions.then(wrappedTaskExecutor).isInstanceOf(LazyTraceThreadPoolTaskExecutor.class);
		BDDAssertions.then(AopUtils.isCglibProxy(wrappedTaskExecutor)).isFalse();
		BDDAssertions.then(AopUtils.isJdkDynamicProxy(wrappedTaskExecutor)).isFalse();
		threadPoolTaskExecutor.shutdown();
	}

	@Test
	public void proxy_is_not_needed() throws Exception {
		boolean isProxyNeeded = new ExecutorInstrumentor(() -> Collections.singletonList("fooExecutor"), beanFactory)
				.isProxyNeeded("fooExecutor");

		BDDAssertions.then(isProxyNeeded).isFalse();
	}

	@Test
	public void proxy_is_needed() throws Exception {
		boolean isProxyNeeded = new ExecutorInstrumentor(Collections::emptyList, beanFactory)
				.isProxyNeeded("fooExecutor");

		BDDAssertions.then(isProxyNeeded).isTrue();
	}

	@Test
	public void should_not_create_proxy() throws Exception {
		Object o = new ExecutorInstrumentor(() -> Collections.singletonList("fooExecutor"), beanFactory)
				.instrument(new ThreadPoolTaskExecutor(), "fooExecutor");

		BDDAssertions.then(o).isInstanceOf(ThreadPoolTaskExecutor.class);
		BDDAssertions.then(AopUtils.isCglibProxy(o)).isFalse();
	}

	@Test
	public void should_throw_real_exception_when_using_proxy() throws Exception {
		Object o = new ExecutorInstrumentor(Collections::emptyList, beanFactory)
				.instrument(new RejectedExecutionExecutor(), "fooExecutor");

		BDDAssertions.then(o).isInstanceOf(RejectedExecutionExecutor.class);
		BDDAssertions.then(AopUtils.isCglibProxy(o)).isTrue();
		BDDAssertions.thenThrownBy(() -> ((RejectedExecutionExecutor) o).execute(() -> {
		})).isInstanceOf(RejectedExecutionException.class).hasMessage("rejected");
	}

	// #1463
	@Test
	public void should_not_double_instrument_traced_executors() throws Exception {
		LazyTraceThreadPoolTaskExecutor lazyTraceThreadPoolTaskExecutor = BDDMockito
				.mock(LazyTraceThreadPoolTaskExecutor.class);
		Object o = new ExecutorInstrumentor(Collections::emptyList, beanFactory)
				.instrument(lazyTraceThreadPoolTaskExecutor, "executor");
		BDDAssertions.then(o).isSameAs(lazyTraceThreadPoolTaskExecutor);

		TraceableExecutorService traceableExecutorService = BDDMockito.mock(TraceableExecutorService.class);
		o = new ExecutorInstrumentor(Collections::emptyList, beanFactory).instrument(traceableExecutorService,
				"executor");
		BDDAssertions.then(o).isSameAs(traceableExecutorService);

		LazyTraceAsyncTaskExecutor lazyTraceAsyncTaskExecutor = BDDMockito.mock(LazyTraceAsyncTaskExecutor.class);
		o = new ExecutorInstrumentor(Collections::emptyList, beanFactory).instrument(lazyTraceAsyncTaskExecutor,
				"executor");
		BDDAssertions.then(o).isSameAs(lazyTraceAsyncTaskExecutor);

		LazyTraceExecutor lazyTraceExecutor = BDDMockito.mock(LazyTraceExecutor.class);
		o = new ExecutorInstrumentor(Collections::emptyList, beanFactory).instrument(lazyTraceExecutor, "executor");
		BDDAssertions.then(o).isSameAs(lazyTraceExecutor);

	}

	// #1569
	@Test
	public void should_use_jdk_proxy_when_executor_has_any_final_methods() {
		ExecutorInstrumentor beanPostProcessor = new ExecutorInstrumentor(Collections::emptyList, beanFactory);

		Executor wrappedExecutor = (Executor) beanPostProcessor.instrument(new ExecutorWithFinalMethod(),
				"executorWithFinalMethod");

		BDDAssertions.then(AopUtils.isJdkDynamicProxy(wrappedExecutor)).isTrue();
		BDDAssertions.then(AopUtils.isCglibProxy(wrappedExecutor)).isFalse();
		AtomicBoolean wasCalled = new AtomicBoolean(false);
		wrappedExecutor.execute(() -> {
			wasCalled.set(true);
		});
		BDDAssertions.then(wasCalled).isTrue();
	}

	// #1569
	@Test
	public void should_use_jdk_proxy_when_executor_has_an_inherited_final_methods() {
		ExecutorInstrumentor beanPostProcessor = new ExecutorInstrumentor(Collections::emptyList, beanFactory);

		Executor wrappedExecutor = (Executor) beanPostProcessor.instrument(new ExecutorWithInheritedFinalMethod(),
				"executorWithFinalMethod");

		BDDAssertions.then(AopUtils.isJdkDynamicProxy(wrappedExecutor)).isTrue();
		BDDAssertions.then(AopUtils.isCglibProxy(wrappedExecutor)).isFalse();
		AtomicBoolean wasCalled = new AtomicBoolean(false);
		wrappedExecutor.execute(() -> {
			wasCalled.set(true);
		});
		BDDAssertions.then(wasCalled).isTrue();
	}

	class Foo implements Executor {

		@Override
		public void execute(Runnable command) {

		}

	}

	class FooThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {

	}

	class RejectedExecutionExecutor implements Executor {

		@Override
		public void execute(Runnable task) {
			throw new RejectedExecutionException("rejected");
		}

	}

	static class DirectTaskExecutor extends SimpleAsyncTaskExecutor {

		@Override
		public final <T> Future<T> submit(Callable<T> callable) {
			return super.submit(callable);
		}

		@Override
		protected void doExecute(Runnable task) {
			task.run();
		}

	}

	static class PoolTaskExecutor extends ThreadPoolTaskExecutor {

		@Override
		public final void execute(Runnable task, long startTimeout) {
			super.execute(task, startTimeout);
		}

	}

	static class ExecutorWithFinalMethod implements Executor {

		@Override
		public void execute(Runnable command) {
			command.run();
		}

		public final void foo() {

		}

	}

	static class ExecutorWithInheritedFinalMethod extends ExecutorWithFinalMethod implements Executor {

		@Override
		public void execute(Runnable command) {
			command.run();
		}

	}

}
