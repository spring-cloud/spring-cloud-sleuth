/*
 * Copyright 2013-2018 the original author or authors.
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brave.Tracing;
import org.aopalliance.aop.Advice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/**
 * @author Marcin Grzejszczak
 * @author Denys Ivano
 */
@RunWith(MockitoJUnitRunner.class)
public class ExecutorBeanPostProcessorTests {

	@Mock
	BeanFactory beanFactory;

	Tracing tracing = Tracing.newBuilder().build();

	private SleuthAsyncProperties sleuthAsyncProperties;

	@Before
	public void setup() {
		this.sleuthAsyncProperties = new SleuthAsyncProperties();
		Mockito.when(this.beanFactory.getBean(SleuthAsyncProperties.class))
				.thenReturn(this.sleuthAsyncProperties);
		Mockito.when(this.beanFactory.getBean(Tracing.class)).thenReturn(this.tracing);
		Mockito.when(this.beanFactory.getBean(SpanNamer.class))
				.thenReturn(new DefaultSpanNamer());
		Mockito.when(this.beanFactory.getBean(ContextRefreshedListener.class))
				.thenReturn(new ContextRefreshedListener(true));
	}

	@After
	public void clear() {
		this.tracing.close();
	}

	@Test
	public void should_create_a_cglib_proxy_by_default() throws Exception {
		Object o = new ExecutorBeanPostProcessor(this.beanFactory)
				.postProcessAfterInitialization(new Foo(), "foo");

		then(o).isInstanceOf(Foo.class);
		then(ClassUtils.isCglibProxy(o)).isTrue();
	}

	class Foo implements Executor {

		@Override
		public void execute(Runnable command) {

		}

	}

	@Test
	public void should_fallback_to_sleuth_implementation_when_cglib_cannot_be_created()
			throws Exception {
		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();

		Object o = new ExecutorBeanPostProcessor(this.beanFactory)
				.postProcessAfterInitialization(service, "foo");

		then(o).isInstanceOf(TraceableExecutorService.class);
		service.shutdown();
	}

	@Test
	public void should_fallback_to_default_implementation_when_exception_thrown()
			throws Exception {
		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		ExecutorBeanPostProcessor bpp = new ExecutorBeanPostProcessor(this.beanFactory) {

			@Override
			Object createProxy(Object bean, boolean cglibProxy, Advice advice) {
				throw new AopConfigException("foo");
			}

		};

		Object wrappedService = bpp.postProcessAfterInitialization(service, "foo");

		then(wrappedService).isInstanceOf(TraceableExecutorService.class);
		service.shutdown();
	}

	@Test
	public void should_create_a_cglib_proxy_by_default_for_ThreadPoolTaskExecutor()
			throws Exception {
		Object o = new ExecutorBeanPostProcessor(this.beanFactory)
				.postProcessAfterInitialization(new FooThreadPoolTaskExecutor(), "foo");

		then(o).isInstanceOf(FooThreadPoolTaskExecutor.class);
		then(ClassUtils.isCglibProxy(o)).isTrue();
	}

	class FooThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {

	}

	@Test
	public void should_throw_exception_when_it_is_not_possible_to_create_any_proxy_for_ThreadPoolTaskExecutor()
			throws Exception {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		ExecutorBeanPostProcessor bpp = new ExecutorBeanPostProcessor(this.beanFactory) {
			@Override
			Object createThreadPoolTaskExecutorProxy(Object bean, boolean cglibProxy,
					ThreadPoolTaskExecutor executor) {
				throw new AopConfigException("foo");
			}
		};

		thenThrownBy(() -> bpp.postProcessAfterInitialization(taskExecutor, "foo"))
				.isInstanceOf(AopConfigException.class).hasMessage("foo");
	}

	@Test
	public void should_fallback_to_sleuth_impl_when_it_is_not_possible_to_create_any_proxy_for_ExecutorService()
			throws Exception {
		ExecutorService service = BDDMockito.mock(ExecutorService.class);
		ExecutorBeanPostProcessor bpp = new ExecutorBeanPostProcessor(this.beanFactory) {
			@Override
			Object getObject(ProxyFactoryBean factory) {
				throw new AopConfigException("foo");
			}
		};

		Object o = bpp.postProcessAfterInitialization(service, "foo");

		then(o).isInstanceOf(TraceableExecutorService.class);
	}

	@Test
	public void should_throw_exception_from_the_wrapped_object() throws Exception {
		ExecutorService service = exceptionThrowingExecutorService();
		ExecutorBeanPostProcessor bpp = new ExecutorBeanPostProcessor(this.beanFactory);

		ExecutorService o = (ExecutorService) bpp.postProcessAfterInitialization(service,
				"foo");

		thenThrownBy(() -> o.submit((Callable<Object>) () -> "hello")).hasMessage("foo")
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
			public boolean awaitTermination(long timeout, TimeUnit unit)
					throws InterruptedException {
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
			public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
					throws InterruptedException {
				return null;
			}

			@Override
			public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
					long timeout, TimeUnit unit) throws InterruptedException {
				return null;
			}

			@Override
			public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
					throws InterruptedException, ExecutionException {
				return null;
			}

			@Override
			public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout,
					TimeUnit unit)
					throws InterruptedException, ExecutionException, TimeoutException {
				return null;
			}
		};
	}

	@Test
	public void proxy_is_not_needed() throws Exception {
		this.sleuthAsyncProperties
				.setIgnoredBeans(Collections.singletonList("fooExecutor"));

		boolean isProxyNeeded = new ExecutorBeanPostProcessor(this.beanFactory)
				.isProxyNeeded("fooExecutor");

		then(isProxyNeeded).isFalse();
	}

	@Test
	public void proxy_is_needed() throws Exception {
		boolean isProxyNeeded = new ExecutorBeanPostProcessor(this.beanFactory)
				.isProxyNeeded("fooExecutor");

		then(isProxyNeeded).isTrue();
	}

	@Test
	public void should_not_create_proxy() throws Exception {
		this.sleuthAsyncProperties
				.setIgnoredBeans(Collections.singletonList("fooExecutor"));

		Object o = new ExecutorBeanPostProcessor(this.beanFactory)
				.postProcessAfterInitialization(new ThreadPoolTaskExecutor(),
						"fooExecutor");

		then(o).isInstanceOf(ThreadPoolTaskExecutor.class);
		then(ClassUtils.isCglibProxy(o)).isFalse();
	}

	@Test
	public void should_throw_real_exception_when_using_proxy() throws Exception {
		// for LazyTraceExecutor
		Mockito.when(this.beanFactory.getBean(Tracing.class))
				.thenReturn(Tracing.newBuilder().build());
		Mockito.when(this.beanFactory.getBean(SpanNamer.class))
				.thenReturn(new DefaultSpanNamer());

		Object o = new ExecutorBeanPostProcessor(this.beanFactory)
				.postProcessAfterInitialization(new RejectedExecutionExecutor(),
						"fooExecutor");

		then(o).isInstanceOf(RejectedExecutionExecutor.class);
		then(ClassUtils.isCglibProxy(o)).isTrue();
		thenThrownBy(() -> ((RejectedExecutionExecutor) o).execute(() -> {
		})).isInstanceOf(RejectedExecutionException.class).hasMessage("rejected");
	}

	class RejectedExecutionExecutor implements Executor {

		@Override
		public void execute(Runnable task) {
			throw new RejectedExecutionException("rejected");
		}

	}

}