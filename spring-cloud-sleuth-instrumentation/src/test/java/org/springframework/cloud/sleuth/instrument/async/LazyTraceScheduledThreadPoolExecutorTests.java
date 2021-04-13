/*
 * Copyright 2013-2021 the original author or authors.
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

import java.lang.reflect.Method;
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
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;

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
		this.executor = spy(new LazyTraceScheduledThreadPoolExecutor(1, beanFactory, delegate, BEAN_NAME) {
			@Override
			boolean isContextUnusable() {
				return false;
			}

			@Override
			boolean isMethodOverridden(Method originalMethod) {
				if (JRE.currentVersion().ordinal() >= JRE.JAVA_16.ordinal()) {
					return false;
				}
				return true;
			}
		});
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

		new LazyTraceScheduledThreadPoolExecutor(10, beanFactory, executor, null) {
			@Override
			boolean isMethodOverridden(Method originalMethod) {
				if (JRE.currentVersion().ordinal() >= JRE.JAVA_16.ordinal()) {
					return false;
				}
				return true;
			}
		}.finalize();

		BDDAssertions.then(wasCalled).isFalse();
		BDDAssertions.then(executor.isShutdown()).isFalse();
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_8, max = JRE.JAVA_15)
	public void should_delegate_decorateTask_with_runnable() {
		final Runnable runnable = mock(Runnable.class);
		final RunnableScheduledFuture<String> value = mock(RunnableScheduledFuture.class);
		final RunnableScheduledFuture<String> expected = mock(RunnableScheduledFuture.class);
		doReturn(expected).when(delegate).decorateTask(any(Runnable.class), eq(value));

		final Future<?> actual = executor.decorateTask(runnable, value);

		assertThat(actual).isEqualTo(expected);
		verify(delegate).decorateTask(runnableCaptor.capture(), eq(value));
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_8, max = JRE.JAVA_15)
	public void should_delegate_decorateTask_with_callable() {
		final Callable callable = mock(Callable.class);
		final RunnableScheduledFuture<String> value = mock(RunnableScheduledFuture.class);
		final RunnableScheduledFuture<String> expected = mock(RunnableScheduledFuture.class);
		doReturn(expected).when(delegate).decorateTask(any(Callable.class), eq(value));

		final Future<?> actual = executor.decorateTask(callable, value);

		assertThat(actual).isEqualTo(expected);
		verify(delegate).decorateTask(callableCaptor.capture(), eq(value));
		assertInstrumentedDelegate(callableCaptor.getValue(), callable);
	}

	@Test
	public void should_delegate_schedule_with_runnable() {
		final Runnable runnable = mock(Runnable.class);
		final long delay = 100;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final ScheduledFuture<String> expected = mock(ScheduledFuture.class);
		doReturn(expected).when(delegate).schedule(any(Runnable.class), eq(delay), eq(timeUnit));

		final Future<?> actual = executor.schedule(runnable, delay, timeUnit);

		assertThat(actual).isEqualTo(expected);
		verify(delegate).schedule(runnableCaptor.capture(), eq(delay), eq(timeUnit));
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_schedule_with_callable() {
		final Callable callable = mock(Callable.class);
		final long delay = 100;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final ScheduledFuture<String> expected = mock(ScheduledFuture.class);
		doReturn(expected).when(delegate).schedule(any(Callable.class), eq(delay), eq(timeUnit));

		final Future<?> actual = executor.schedule(callable, delay, timeUnit);

		assertThat(actual).isEqualTo(expected);
		verify(delegate).schedule(callableCaptor.capture(), eq(delay), eq(timeUnit));
		assertInstrumentedDelegate(callableCaptor.getValue(), callable);
	}

	@Test
	public void should_delegate_scheduleAtFixedRate() {
		final Runnable runnable = mock(Runnable.class);
		final long initialDelay = 1000;
		final long period = 2000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final ScheduledFuture<String> expected = mock(ScheduledFuture.class);
		doReturn(expected).when(delegate).scheduleAtFixedRate(any(Runnable.class), eq(initialDelay), eq(period),
				eq(timeUnit));

		final Future<?> actual = executor.scheduleAtFixedRate(runnable, initialDelay, period, timeUnit);

		assertThat(actual).isEqualTo(expected);
		verify(delegate).scheduleAtFixedRate(runnableCaptor.capture(), eq(initialDelay), eq(period), eq(timeUnit));
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	public void should_delegate_scheduleWithFixedDelay() {
		final Runnable runnable = mock(Runnable.class);
		final long initialDelay = 1000;
		final long period = 2000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final ScheduledFuture<String> expected = mock(ScheduledFuture.class);
		doReturn(expected).when(delegate).scheduleWithFixedDelay(any(Runnable.class), eq(initialDelay), eq(period),
				eq(timeUnit));

		final Future<?> actual = executor.scheduleWithFixedDelay(runnable, initialDelay, period, timeUnit);

		assertThat(actual).isEqualTo(expected);
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
		final boolean expected = true;

		executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(expected);

		verify(delegate).setContinueExistingPeriodicTasksAfterShutdownPolicy(expected);
	}

	@Test
	public void should_delegate_getContinueExistingPeriodicTasksAfterShutdownPolicy() {
		final boolean expected = true;
		doReturn(expected).when(delegate).getContinueExistingPeriodicTasksAfterShutdownPolicy();

		final boolean actual = executor.getContinueExistingPeriodicTasksAfterShutdownPolicy();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_setExecuteExistingDelayedTasksAfterShutdownPolicy() {
		final boolean expected = true;

		executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(expected);

		verify(delegate).setExecuteExistingDelayedTasksAfterShutdownPolicy(expected);
	}

	@Test
	public void should_delegate_getExecuteExistingDelayedTasksAfterShutdownPolicy() {
		final boolean expected = true;
		doReturn(expected).when(delegate).getExecuteExistingDelayedTasksAfterShutdownPolicy();

		final boolean actual = executor.getExecuteExistingDelayedTasksAfterShutdownPolicy();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_setRemoveOnCancelPolicy() {
		final boolean expected = true;

		executor.setRemoveOnCancelPolicy(expected);

		verify(delegate).setRemoveOnCancelPolicy(expected);
	}

	@Test
	public void should_delegate_getRemoveOnCancelPolicy() {
		final boolean expected = true;
		doReturn(expected).when(delegate).getRemoveOnCancelPolicy();

		final boolean actual = executor.getRemoveOnCancelPolicy();

		assertThat(actual).isEqualTo(expected);
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
		final BlockingQueue<Runnable> expected = mock(BlockingQueue.class);
		doReturn(expected).when(delegate).getQueue();

		final BlockingQueue<Runnable> actual = executor.getQueue();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_isShutdown() {
		final boolean expected = true;
		doReturn(expected).when(delegate).isShutdown();

		final boolean actual = executor.isShutdown();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_isTerminating() {
		final boolean expected = true;
		doReturn(expected).when(delegate).isTerminating();

		final boolean actual = executor.isTerminating();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_isTerminated() {
		final boolean expected = true;
		doReturn(expected).when(delegate).isTerminated();

		final boolean actual = executor.isTerminated();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_awaitTermination() throws Exception {
		final long timeout = 1000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		final boolean expected = true;
		doReturn(expected).when(delegate).awaitTermination(timeout, timeUnit);

		final boolean terminated = executor.awaitTermination(timeout, timeUnit);

		assertThat(terminated).isEqualTo(expected);
	}

	@Test
	public void should_delegate_setThreadFactory() {
		final ThreadFactory expected = mock(ThreadFactory.class);

		executor.setThreadFactory(expected);

		verify(delegate).setThreadFactory(expected);
	}

	@Test
	public void should_delegate_getThreadFactory() {
		final ThreadFactory expected = mock(ThreadFactory.class);
		doReturn(expected).when(delegate).getThreadFactory();

		final ThreadFactory actual = executor.getThreadFactory();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_setRejectedExecutionHandler() {
		final RejectedExecutionHandler expected = mock(RejectedExecutionHandler.class);

		executor.setRejectedExecutionHandler(expected);

		verify(delegate).setRejectedExecutionHandler(expected);
	}

	@Test
	public void should_delegate_getRejectedExecutionHandler() {
		final RejectedExecutionHandler expected = mock(RejectedExecutionHandler.class);
		doReturn(expected).when(delegate).getRejectedExecutionHandler();

		final RejectedExecutionHandler actual = executor.getRejectedExecutionHandler();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_setCorePoolSize() {
		final int expected = 1000;

		executor.setCorePoolSize(expected);

		verify(delegate).setCorePoolSize(expected);
	}

	@Test
	public void should_delegate_getCorePoolSize() {
		final int expected = 1000;
		doReturn(expected).when(delegate).getCorePoolSize();

		final int actual = executor.getCorePoolSize();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_prestartCoreThread() {
		final boolean expected = true;
		doReturn(expected).when(delegate).prestartCoreThread();

		final boolean actual = executor.prestartCoreThread();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_prestartAllCoreThreads() {
		final int expected = 1000;
		doReturn(expected).when(delegate).prestartAllCoreThreads();

		final int actual = executor.prestartAllCoreThreads();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_allowsCoreThreadTimeOut() {
		final boolean expected = true;
		doReturn(expected).when(delegate).allowsCoreThreadTimeOut();

		final boolean actual = executor.allowsCoreThreadTimeOut();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_allowCoreThreadTimeOut() {
		final boolean expected = true;

		executor.allowCoreThreadTimeOut(expected);

		verify(delegate).allowCoreThreadTimeOut(expected);
	}

	@Test
	public void should_delegate_setMaximumPoolSize() {
		final int expected = 1000;

		executor.setMaximumPoolSize(expected);

		verify(delegate).setMaximumPoolSize(expected);
	}

	@Test
	public void should_delegate_getMaximumPoolSize() {
		final int expected = 1000;
		doReturn(expected).when(delegate).getMaximumPoolSize();

		final int actual = executor.getMaximumPoolSize();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_setKeepAliveTime() {
		final long expected = 1000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;

		executor.setKeepAliveTime(expected, timeUnit);

		verify(delegate).setKeepAliveTime(expected, timeUnit);
	}

	@Test
	public void should_delegate_getKeepAliveTime() {
		final long expected = 1000;
		final TimeUnit timeUnit = TimeUnit.SECONDS;
		doReturn(expected).when(delegate).getKeepAliveTime(timeUnit);

		final long actual = executor.getKeepAliveTime(timeUnit);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_remove() {
		final Runnable expected = mock(Runnable.class);

		executor.remove(expected);

		verify(delegate).remove(expected);
	}

	@Test
	public void should_delegate_purge() {
		executor.purge();

		verify(delegate).purge();
	}

	@Test
	public void should_delegate_getPoolSize() {
		final int expected = 1000;
		doReturn(expected).when(delegate).getPoolSize();

		final int actual = executor.getPoolSize();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_getActiveCount() {
		final int expected = 1000;
		doReturn(expected).when(delegate).getActiveCount();

		final int actual = executor.getActiveCount();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_getLargestPoolSize() {
		final int expected = 1000;
		doReturn(expected).when(delegate).getLargestPoolSize();

		final int actual = executor.getLargestPoolSize();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_getTaskCount() {
		final long value = 1000;
		doReturn(value).when(delegate).getTaskCount();

		final long actual = executor.getTaskCount();

		assertThat(actual).isEqualTo(value);
	}

	@Test
	public void should_delegate_getCompletedTaskCount() {
		final long expected = 1000;
		doReturn(expected).when(delegate).getCompletedTaskCount();

		final long actual = executor.getCompletedTaskCount();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_toString() {
		final String expected = "testing";
		doReturn(expected).when(delegate).toString();

		final String actual = executor.toString();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_8, max = JRE.JAVA_15)
	public void should_delegate_beforeExecute() {
		final Thread thread = mock(Thread.class);
		final Runnable expected = mock(Runnable.class);

		executor.beforeExecute(thread, expected);

		verify(delegate).beforeExecute(eq(thread), runnableCaptor.capture());
		assertInstrumentedDelegate(runnableCaptor.getValue(), expected);
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_8, max = JRE.JAVA_15)
	public void should_delegate_afterExecute() {
		final Throwable throwable = mock(Throwable.class);
		final Runnable expected = mock(Runnable.class);

		executor.afterExecute(expected, throwable);

		verify(delegate).afterExecute(runnableCaptor.capture(), eq(throwable));
		assertInstrumentedDelegate(runnableCaptor.getValue(), expected);
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_8, max = JRE.JAVA_15)
	public void should_delegate_terminated() {
		executor.terminated();

		verify(delegate).terminated();
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_8, max = JRE.JAVA_15)
	public void should_delegate_newTaskForRunnable() {
		final Runnable runnable = mock(Runnable.class);
		final String expected = "testing";
		final RunnableFuture<String> future = mock(RunnableFuture.class);
		doReturn(future).when(delegate).newTaskFor(any(Runnable.class), eq(expected));

		final Future<?> actual = executor.newTaskFor(runnable, expected);

		assertThat(actual).isEqualTo(future);
		verify(delegate).newTaskFor(runnableCaptor.capture(), eq(expected));
		assertInstrumentedDelegate(runnableCaptor.getValue(), runnable);
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_8, max = JRE.JAVA_15)
	public void should_delegate_newTaskForCallable() {
		final Callable<String> callable = mock(Callable.class);
		final RunnableFuture<String> expected = mock(RunnableFuture.class);
		doReturn(expected).when(delegate).newTaskFor(any());

		final Future<?> actual = executor.newTaskFor(callable);

		assertThat(actual).isEqualTo(expected);
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
		final List<Future<String>> expected = Arrays.asList(mock(Future.class), mock(Future.class), mock(Future.class));
		doReturn(expected).when(delegate).invokeAll(wrapped, timeout, timeUnit);

		final List<Future<String>> actual = executor.invokeAll(tasks, timeout, timeUnit);

		assertThat(actual).isEqualTo(expected);
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
		final String expected = "completed";
		doReturn(expected).when(delegate).invokeAny(wrapped, timeout, timeUnit);

		final String actual = executor.invokeAny(tasks, timeout, timeUnit);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_wrapCallableCollection() {
		final Callable<String> expected1 = mock(Callable.class);
		final Callable<String> expected2 = mock(Callable.class);
		final Callable<String> expected3 = mock(Callable.class);

		final Collection<? extends Callable<String>> actual = executor
				.wrapCallableCollection(Arrays.asList(expected1, expected2, expected3));

		assertThat(actual).extracting("tracer", "delegate", "parent", "spanName").containsExactly(
				tuple(tracer, expected1, parent, BEAN_NAME), tuple(tracer, expected2, parent, BEAN_NAME),
				tuple(tracer, expected3, parent, BEAN_NAME));
	}

	@Test
	public void should_delegate_invokeAny() throws Exception {
		final Collection<Callable<String>> tasks = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		final Collection<Callable<String>> wrapped = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		doReturn(wrapped).when(executor).wrapCallableCollection(tasks);
		final String expected = "completed";
		doReturn(expected).when(delegate).invokeAny(wrapped);

		final String actual = executor.invokeAny(tasks);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void should_delegate_invokeAll() throws Exception {
		final Collection<Callable<String>> tasks = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		final Collection<Callable<String>> wrapped = Arrays.asList(mock(Callable.class), mock(Callable.class),
				mock(Callable.class));
		doReturn(wrapped).when(executor).wrapCallableCollection(tasks);
		final List<Future<String>> expected = Arrays.asList(mock(Future.class), mock(Future.class), mock(Future.class));
		doReturn(expected).when(delegate).invokeAll(wrapped);

		final List<Future<String>> actual = executor.invokeAll(tasks);

		assertThat(actual).isEqualTo(expected);
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
