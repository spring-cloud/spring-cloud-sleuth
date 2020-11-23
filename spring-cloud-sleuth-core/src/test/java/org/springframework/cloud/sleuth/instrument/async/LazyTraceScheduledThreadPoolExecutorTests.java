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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LazyTraceScheduledThreadPoolExecutorTests {

	private static final String BEAN_NAME = "mock";

	@Mock(lenient = true)
	private BeanFactory beanFactory;

	@Mock(lenient = true)
	private ScheduledThreadPoolExecutorImpl delegate;

	@Mock(lenient = true)
	private Tracer tracer;

	@Mock(lenient = true)
	private Span parent;

	@Mock(lenient = true)
	private SpanNamer spanNamer;

	private LazyTraceScheduledThreadPoolExecutor executor;

	@Captor
	private ArgumentCaptor<Runnable> runnableCaptor;

	@Captor
	private ArgumentCaptor<Callable> callableCaptor;

	@BeforeEach
	public void setup() {
		doReturn(tracer).when(beanFactory).getBean(Tracer.class);
		doReturn(parent).when(tracer).currentSpan();
		doReturn(spanNamer).when(beanFactory).getBean(SpanNamer.class);
		this.executor = spy(new LazyTraceScheduledThreadPoolExecutor(1, beanFactory, delegate, BEAN_NAME));
	}

	@Test
	public void should_not_finalize_the_delegate_since_its_a_shared_instance() {
		AtomicBoolean wasCalled = new AtomicBoolean();
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(10) {
			@Override
			protected void finalize() {
				super.finalize();
				wasCalled.set(true);
			}
		};
		BeanFactory beanFactory = mock(BeanFactory.class);

		new LazyTraceScheduledThreadPoolExecutor(10, beanFactory, executor, null).finalize();

		BDDAssertions.then(wasCalled).isFalse();
		BDDAssertions.then(executor.isShutdown()).isFalse();
	}

	@Test
	public void should_delegate_decorateTask_with_runnable() {
		final Runnable runnable = mock(Runnable.class);
		final RunnableScheduledFuture<String> value = mock(RunnableScheduledFuture.class);
		final RunnableScheduledFuture<String> future = mock(RunnableScheduledFuture.class);
		doReturn(future).when(delegate).decorateTask(any(Runnable.class), eq(value));

		assertThat((Future<?>) executor.decorateTask(runnable, value)).isEqualTo(future);

		verify(delegate).decorateTask(runnableCaptor.capture(), eq(value));
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_decorateTask_with_callable() {
		final Callable callable = mock(Callable.class);
		final RunnableScheduledFuture<String> value = mock(RunnableScheduledFuture.class);
		final RunnableScheduledFuture<String> future = mock(RunnableScheduledFuture.class);
		doReturn(future).when(delegate).decorateTask(any(Callable.class), eq(value));

		assertThat((Future<?>) executor.decorateTask(callable, value)).isEqualTo(future);

		verify(delegate).decorateTask(callableCaptor.capture(), eq(value));
		assertInstrumentedDelegate(callableCaptor.getValue(), callable);
	}

	@Test
	public void should_delegate_schedule_with_runnable() {
		final Runnable runnable = mock(Runnable.class);
		final long delay = 100;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final ScheduledFuture<String> future = mock(ScheduledFuture.class);
		doReturn(future).when(delegate).schedule(any(Runnable.class), eq(delay), eq(timeUnit));

		assertThat((Future<?>) executor.schedule(runnable, delay, timeUnit)).isEqualTo(future);

		verify(delegate).schedule(runnableCaptor.capture(), eq(delay), eq(timeUnit));
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_schedule_with_callable() {
		final Callable callable = mock(Callable.class);
		final long delay = 100;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final ScheduledFuture<String> future = mock(ScheduledFuture.class);
		doReturn(future).when(delegate).schedule(any(Callable.class), eq(delay), eq(timeUnit));

		assertThat((Future<?>) executor.schedule(callable, delay, timeUnit)).isEqualTo(future);

		verify(delegate).schedule(callableCaptor.capture(), eq(delay), eq(timeUnit));
		assertInstrumentedDelegate(callableCaptor.getValue(), callable);
	}

	@Test
	public void should_delegate_scheduleAtFixedRate() {
		final Runnable runnable = mock(Runnable.class);
		final long initialDelay = 1000;
		final long period = 2000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final ScheduledFuture<String> future = mock(ScheduledFuture.class);
		doReturn(future).when(delegate).scheduleAtFixedRate(any(Runnable.class), eq(initialDelay), eq(period),
				eq(timeUnit));

		assertThat((Future<?>) executor.scheduleAtFixedRate(runnable, initialDelay, period, timeUnit))
				.isEqualTo(future);

		verify(delegate).scheduleAtFixedRate(runnableCaptor.capture(), eq(initialDelay), eq(period), eq(timeUnit));
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_scheduleWithFixedDelay() {
		final Runnable runnable = mock(Runnable.class);
		final long initialDelay = 1000;
		final long period = 2000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final ScheduledFuture<String> future = mock(ScheduledFuture.class);
		doReturn(future).when(delegate).scheduleWithFixedDelay(any(Runnable.class), eq(initialDelay), eq(period),
				eq(timeUnit));

		assertThat((Future<?>) executor.scheduleWithFixedDelay(runnable, initialDelay, period, timeUnit))
				.isEqualTo(future);

		verify(delegate).scheduleWithFixedDelay(runnableCaptor.capture(), eq(initialDelay), eq(period), eq(timeUnit));
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_execute() {
		final Runnable runnable = mock(Runnable.class);

		executor.execute(runnable);

		verify(delegate).execute(runnableCaptor.capture());
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_submit_with_runnable() {
		final Runnable runnable = mock(Runnable.class);

		executor.submit(runnable);

		verify(delegate).submit(runnableCaptor.capture());
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_submit_with_runnable_result() {
		final Object result = new Object();
		final Runnable runnable = mock(Runnable.class);

		executor.submit(runnable, result);

		verify(delegate).submit(runnableCaptor.capture(), eq(result));
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_submit_with_callable() {
		final Callable callable = mock(Callable.class);

		executor.submit(callable);

		verify(delegate).submit(callableCaptor.capture());
		assertInstrumentedDelegate(callableCaptor.getValue(), callable);
	}

	@Test
	public void should_delegate_setContinueExistingPeriodicTasksAfterShutdownPolicy() {
		final boolean value = true;

		executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(value);

		verify(delegate).setContinueExistingPeriodicTasksAfterShutdownPolicy(value);
	}

	@Test
	public void should_delegate_getContinueExistingPeriodicTasksAfterShutdownPolicy() {
		final boolean value = true;
		doReturn(value).when(delegate).getContinueExistingPeriodicTasksAfterShutdownPolicy();

		assertThat(executor.getContinueExistingPeriodicTasksAfterShutdownPolicy()).isEqualTo(value);
	}

	@Test
	public void should_delegate_setExecuteExistingDelayedTasksAfterShutdownPolicy() {
		final boolean value = true;

		executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(value);

		verify(delegate).setExecuteExistingDelayedTasksAfterShutdownPolicy(value);
	}

	@Test
	public void should_delegate_getExecuteExistingDelayedTasksAfterShutdownPolicy() {
		final boolean value = true;
		doReturn(value).when(delegate).getExecuteExistingDelayedTasksAfterShutdownPolicy();

		assertThat(executor.getExecuteExistingDelayedTasksAfterShutdownPolicy()).isEqualTo(value);
	}

	@Test
	public void should_delegate_setRemoveOnCancelPolicy() {
		final boolean value = true;

		executor.setRemoveOnCancelPolicy(value);

		verify(delegate).setRemoveOnCancelPolicy(value);
	}

	@Test
	public void should_delegate_getRemoveOnCancelPolicy() {
		final boolean value = true;
		doReturn(value).when(delegate).getRemoveOnCancelPolicy();

		assertThat(executor.getRemoveOnCancelPolicy()).isEqualTo(value);
	}

	@Test
	public void should_delegate_shutdown() {
		executor.shutdown();

		verify(delegate).shutdown();
	}

	@Test
	public void should_delegate_shutdownNow() {
		executor.shutdownNow();

		verify(delegate).shutdownNow();
	}

	@Test
	public void should_delegate_getQueue() {
		final BlockingQueue<Runnable> value = mock(BlockingQueue.class);
		doReturn(value).when(delegate).getQueue();
		assertThat(executor.getQueue()).isEqualTo(value);
	}

	@Test
	public void should_delegate_isShutdown() {
		final boolean value = true;
		doReturn(value).when(delegate).isShutdown();

		assertThat(executor.isShutdown()).isEqualTo(value);
	}

	@Test
	public void should_delegate_isTerminating() {
		final boolean value = true;
		doReturn(value).when(delegate).isTerminating();

		assertThat(executor.isTerminating()).isEqualTo(value);
	}

	@Test
	public void should_delegate_isTerminated() {
		final boolean value = true;
		doReturn(value).when(delegate).isTerminated();

		assertThat(executor.isTerminated()).isEqualTo(value);
	}

	@Test
	public void should_delegate_awaitTermination() throws Exception {
		final long timeout = 1000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final boolean value = true;
		doReturn(value).when(delegate).awaitTermination(timeout, timeUnit);

		assertThat(executor.awaitTermination(timeout, timeUnit)).isEqualTo(value);
	}

	@Test
	public void should_delegate_setThreadFactory() {
		final ThreadFactory value = mock(ThreadFactory.class);

		executor.setThreadFactory(value);

		verify(delegate).setThreadFactory(value);
	}

	@Test
	public void should_delegate_getThreadFactory() {
		final ThreadFactory value = mock(ThreadFactory.class);
		doReturn(value).when(delegate).getThreadFactory();

		assertThat(executor.getThreadFactory()).isEqualTo(value);
	}

	@Test
	public void should_delegate_setRejectedExecutionHandler() {
		final RejectedExecutionHandler value = mock(RejectedExecutionHandler.class);

		executor.setRejectedExecutionHandler(value);

		verify(delegate).setRejectedExecutionHandler(value);
	}

	@Test
	public void should_delegate_getRejectedExecutionHandler() {
		final RejectedExecutionHandler value = mock(RejectedExecutionHandler.class);
		doReturn(value).when(delegate).getRejectedExecutionHandler();

		assertThat(executor.getRejectedExecutionHandler()).isEqualTo(value);
	}

	@Test
	public void should_delegate_setCorePoolSize() {
		final int value = 1000;

		executor.setCorePoolSize(value);

		verify(delegate).setCorePoolSize(value);
	}

	@Test
	public void should_delegate_getCorePoolSize() {
		final int value = 1000;
		doReturn(value).when(delegate).getCorePoolSize();

		assertThat(executor.getCorePoolSize()).isEqualTo(value);
	}

	@Test
	public void should_delegate_prestartCoreThread() {
		final boolean value = true;
		doReturn(value).when(delegate).prestartCoreThread();

		assertThat(executor.prestartCoreThread()).isEqualTo(value);
	}

	@Test
	public void should_delegate_prestartAllCoreThreads() {
		final int value = 1000;
		doReturn(value).when(delegate).prestartAllCoreThreads();

		assertThat(executor.prestartAllCoreThreads()).isEqualTo(value);
	}

	@Test
	public void should_delegate_allowsCoreThreadTimeOut() {
		final boolean value = true;
		doReturn(value).when(delegate).allowsCoreThreadTimeOut();

		assertThat(executor.allowsCoreThreadTimeOut()).isEqualTo(value);
	}

	@Test
	public void should_delegate_allowCoreThreadTimeOut() {
		final boolean value = true;

		executor.allowCoreThreadTimeOut(value);

		verify(delegate).allowCoreThreadTimeOut(value);
	}

	@Test
	public void should_delegate_setMaximumPoolSize() {
		final int value = 1000;

		executor.setMaximumPoolSize(value);

		verify(delegate).setMaximumPoolSize(value);
	}

	@Test
	public void should_delegate_getMaximumPoolSize() {
		final int value = 1000;
		doReturn(value).when(delegate).getMaximumPoolSize();

		assertThat(executor.getMaximumPoolSize()).isEqualTo(value);
	}

	@Test
	public void should_delegate_setKeepAliveTime() {
		final long value = 1000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;

		executor.setKeepAliveTime(value, timeUnit);

		verify(delegate).setKeepAliveTime(value, timeUnit);
	}

	@Test
	public void should_delegate_getKeepAliveTime() {
		final long value = 1000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		doReturn(value).when(delegate).getKeepAliveTime(timeUnit);

		assertThat(executor.getKeepAliveTime(timeUnit)).isEqualTo(value);
	}

	@Test
	public void should_delegate_remove() {
		final Runnable value = mock(Runnable.class);

		executor.remove(value);

		verify(delegate).remove(value);
	}

	@Test
	public void should_delegate_purge() {
		executor.purge();

		verify(delegate).purge();
	}

	@Test
	public void should_delegate_getPoolSize() {
		final int value = 1000;
		doReturn(value).when(delegate).getPoolSize();

		assertThat(executor.getPoolSize()).isEqualTo(value);
	}

	@Test
	public void should_delegate_getActiveCount() {
		final int value = 1000;
		doReturn(value).when(delegate).getActiveCount();

		assertThat(executor.getActiveCount()).isEqualTo(value);
	}

	@Test
	public void should_delegate_getLargestPoolSize() {
		final int value = 1000;
		doReturn(value).when(delegate).getLargestPoolSize();

		assertThat(executor.getLargestPoolSize()).isEqualTo(value);
	}

	@Test
	public void should_delegate_getTaskCount() {
		final long value = 1000;
		doReturn(value).when(delegate).getTaskCount();

		assertThat(executor.getTaskCount()).isEqualTo(value);
	}

	@Test
	public void should_delegate_getCompletedTaskCount() {
		final long value = 1000;
		doReturn(value).when(delegate).getCompletedTaskCount();

		assertThat(executor.getCompletedTaskCount()).isEqualTo(value);
	}

	@Test
	public void should_delegate_toString() {
		final String value = "testing";
		doReturn(value).when(delegate).toString();

		assertThat(executor.toString()).isEqualTo(value);
	}

	@Test
	public void should_delegate_beforeExecute() {
		final Thread thread = mock(Thread.class);
		final Runnable runnable = mock(Runnable.class);

		executor.beforeExecute(thread, runnable);

		verify(delegate).beforeExecute(eq(thread), runnableCaptor.capture());
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_afterExecute() {
		final Throwable throwable = mock(Throwable.class);
		final Runnable runnable = mock(Runnable.class);

		executor.afterExecute(runnable, throwable);

		verify(delegate).afterExecute(runnableCaptor.capture(), eq(throwable));
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_terminated() {
		executor.terminated();

		verify(delegate).terminated();
	}

	@Test
	public void should_delegate_newTaskForRunnable() {
		final Runnable runnable = mock(Runnable.class);
		final String value = "testing";
		final RunnableFuture<String> future = mock(RunnableFuture.class);
		doReturn(future).when(delegate).newTaskFor(any(Runnable.class), eq(value));
		assertThat(executor.newTaskFor(runnable, value)).isEqualTo(future);
		verify(delegate).newTaskFor(runnableCaptor.capture(), eq(value));
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_newTaskForCallable() {
		final Callable<String> callable = mock(Callable.class);
		final RunnableFuture<String> future = mock(RunnableFuture.class);
		doReturn(future).when(delegate).newTaskFor(any());

		assertThat(executor.newTaskFor(callable)).isEqualTo(future);

		verify(delegate).newTaskFor(callableCaptor.capture());
		assertInstrumentedDelegate(callableCaptor.getValue(), callable);
	}

	@Test
	public void should_delegate_invokeAll_with_timeout() throws Exception {
		final long timeout = 1000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final Collection<Callable<String>> tasks = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		final Collection<Callable<String>> wrapped = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		doReturn(wrapped).when(executor).wrapCallableCollection(tasks);
		final List<Future<String>> futures = Arrays.asList(mock(Future.class), mock(Future.class), mock(Future.class));
		doReturn(futures).when(delegate).invokeAll(wrapped, timeout, timeUnit);

		assertThat(executor.invokeAll(tasks, timeout, timeUnit)).isEqualTo(futures);
	}

	@Test
	public void should_delegate_invokeAny_with_timeout() throws Exception {
		final long timeout = 1000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final Collection<Callable<String>> tasks = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		final Collection<Callable<String>> wrapped = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		doReturn(wrapped).when(executor).wrapCallableCollection(tasks);
		final String completed = "completed";
		doReturn(completed).when(delegate).invokeAny(wrapped, timeout, timeUnit);

		assertThat(executor.invokeAny(tasks, timeout, timeUnit)).isEqualTo(completed);
	}

	@Test
	public void should_wrapCallableCollection() {
		final Callable<String> task1 = mock(Callable.class);
		final Callable<String> task2 = mock(Callable.class);
		final Callable<String> task3 = mock(Callable.class);

		assertThat(executor.wrapCallableCollection(Arrays.asList(task1, task2, task3)))
				.extracting("tracer", "delegate", "parent", "spanName")
				.containsExactly(tuple(tracer, task1, parent, BEAN_NAME), tuple(tracer, task2, parent, BEAN_NAME),
						tuple(tracer, task3, parent, BEAN_NAME));
	}

	@Test
	public void should_delegate_invokeAny() throws Exception {
		final Collection<Callable<String>> tasks = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		final Collection<Callable<String>> wrapped = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		doReturn(wrapped).when(executor).wrapCallableCollection(tasks);
		final String completed = "completed";
		doReturn(completed).when(delegate).invokeAny(wrapped);

		assertThat(executor.invokeAny(tasks)).isEqualTo(completed);
	}

	@Test
	public void should_delegate_invokeAll() throws Exception {
		final Collection<Callable<String>> tasks = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		final Collection<Callable<String>> wrapped = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		doReturn(wrapped).when(executor).wrapCallableCollection(tasks);
		final List<Future<String>> futures = Arrays.asList(mock(Future.class), mock(Future.class), mock(Future.class));
		doReturn(futures).when(delegate).invokeAll(wrapped);

		assertThat(executor.invokeAll(tasks)).isEqualTo(futures);
	}

	void assertInstrumentedDelegate(final Runnable instrumented, final Runnable delegate) {
		assertThat(instrumented).isInstanceOf(TraceRunnable.class).hasFieldOrPropertyWithValue("tracer", tracer)
				.hasFieldOrPropertyWithValue("delegate", delegate).hasFieldOrPropertyWithValue("parent", parent)
				.hasFieldOrPropertyWithValue("spanName", BEAN_NAME);
	}

	void assertInstrumentedDelegate(final Callable instrumented, final Callable delegate) {
		assertThat(instrumented).isInstanceOf(TraceCallable.class).hasFieldOrPropertyWithValue("tracer", tracer)
				.hasFieldOrPropertyWithValue("delegate", delegate).hasFieldOrPropertyWithValue("parent", parent)
				.hasFieldOrPropertyWithValue("spanName", BEAN_NAME);
	}

	private static class ScheduledThreadPoolExecutorImpl extends ScheduledThreadPoolExecutor {

		ScheduledThreadPoolExecutorImpl() {
			super(1);
		}

		// adding these methods here so they are visible for testing/mocking

		@Override
		protected <V> RunnableScheduledFuture<V> decorateTask(Runnable runnable, RunnableScheduledFuture<V> task) {
			return super.decorateTask(runnable, task);
		}

		@Override
		protected <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable, RunnableScheduledFuture<V> task) {
			return super.decorateTask(callable, task);
		}

		@Override
		protected void beforeExecute(Thread t, Runnable r) {

		}

		@Override
		protected void afterExecute(Runnable r, Throwable t) {

		}

		@Override
		protected void terminated() {

		}

		@Override
		protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
			return super.newTaskFor(runnable, value);
		}

		@Override
		protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
			return super.newTaskFor(callable);
		}

	}

}
