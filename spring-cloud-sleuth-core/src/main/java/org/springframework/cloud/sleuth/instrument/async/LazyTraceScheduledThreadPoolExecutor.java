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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brave.Tracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.util.ReflectionUtils;

/**
 * Trace representation of {@link ScheduledThreadPoolExecutor}. Should be used only * as
 * last resort, when any other approaches fail.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.4
 */
// TODO: Think of a better solution than this
class LazyTraceScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

	private static final Log log = LogFactory
			.getLog(LazyTraceScheduledThreadPoolExecutor.class);

	private final BeanFactory beanFactory;

	private final ScheduledThreadPoolExecutor delegate;

	private final Method decorateTaskRunnable;

	private final Method decorateTaskCallable;

	private final Method finalize;

	private final Method beforeExecute;

	private final Method afterExecute;

	private final Method terminated;

	private final Method newTaskForRunnable;

	private final Method newTaskForCallable;

	private Tracing tracing;

	private SpanNamer spanNamer;

	LazyTraceScheduledThreadPoolExecutor(int corePoolSize, BeanFactory beanFactory,
			ScheduledThreadPoolExecutor delegate) {
		super(corePoolSize);
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.decorateTaskRunnable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "decorateTask", Runnable.class,
				RunnableScheduledFuture.class);
		makeAccessibleIfNotNull(this.decorateTaskRunnable);
		this.decorateTaskCallable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "decorateTaskCallable", Callable.class,
				RunnableScheduledFuture.class);
		makeAccessibleIfNotNull(this.decorateTaskCallable);
		this.finalize = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"finalize", null);
		makeAccessibleIfNotNull(this.finalize);
		this.beforeExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"beforeExecute", null);
		makeAccessibleIfNotNull(this.beforeExecute);
		this.afterExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"afterExecute", null);
		makeAccessibleIfNotNull(this.afterExecute);
		this.terminated = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"terminated", null);
		makeAccessibleIfNotNull(this.terminated);
		this.newTaskForRunnable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "newTaskFor", Runnable.class,
				Object.class);
		makeAccessibleIfNotNull(this.newTaskForRunnable);
		this.newTaskForCallable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "newTaskFor", Callable.class,
				Object.class);
		makeAccessibleIfNotNull(this.newTaskForCallable);
	}

	private void makeAccessibleIfNotNull(Method method) {
		if (method != null) {
			ReflectionUtils.makeAccessible(method);
		}
	}

	LazyTraceScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory,
			BeanFactory beanFactory, ScheduledThreadPoolExecutor delegate) {
		super(corePoolSize, threadFactory);
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.decorateTaskRunnable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "decorateTask", Runnable.class,
				RunnableScheduledFuture.class);
		makeAccessibleIfNotNull(this.decorateTaskRunnable);
		this.decorateTaskCallable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "decorateTaskCallable", Callable.class,
				RunnableScheduledFuture.class);
		makeAccessibleIfNotNull(this.decorateTaskCallable);
		this.finalize = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"finalize");
		makeAccessibleIfNotNull(this.finalize);
		this.beforeExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"beforeExecute");
		makeAccessibleIfNotNull(this.beforeExecute);
		this.afterExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"afterExecute", null);
		makeAccessibleIfNotNull(this.afterExecute);
		this.terminated = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"terminated", null);
		makeAccessibleIfNotNull(this.terminated);
		this.newTaskForRunnable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "newTaskFor", Runnable.class,
				Object.class);
		makeAccessibleIfNotNull(this.newTaskForRunnable);
		this.newTaskForCallable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "newTaskFor", Callable.class,
				Object.class);
		makeAccessibleIfNotNull(this.newTaskForCallable);
	}

	LazyTraceScheduledThreadPoolExecutor(int corePoolSize,
			RejectedExecutionHandler handler, BeanFactory beanFactory,
			ScheduledThreadPoolExecutor delegate) {
		super(corePoolSize, handler);
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.decorateTaskRunnable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "decorateTask", Runnable.class,
				RunnableScheduledFuture.class);
		makeAccessibleIfNotNull(this.decorateTaskRunnable);
		this.decorateTaskCallable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "decorateTaskCallable", Callable.class,
				RunnableScheduledFuture.class);
		makeAccessibleIfNotNull(this.decorateTaskCallable);
		this.finalize = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"finalize", null);
		makeAccessibleIfNotNull(this.finalize);
		this.beforeExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"beforeExecute", null);
		makeAccessibleIfNotNull(this.beforeExecute);
		this.afterExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"afterExecute", null);
		makeAccessibleIfNotNull(this.afterExecute);
		this.terminated = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"terminated", null);
		makeAccessibleIfNotNull(this.terminated);
		this.newTaskForRunnable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "newTaskFor", Runnable.class,
				Object.class);
		makeAccessibleIfNotNull(this.newTaskForRunnable);
		this.newTaskForCallable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "newTaskFor", Callable.class,
				Object.class);
		makeAccessibleIfNotNull(this.newTaskForCallable);
	}

	LazyTraceScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory,
			RejectedExecutionHandler handler, BeanFactory beanFactory,
			ScheduledThreadPoolExecutor delegate) {
		super(corePoolSize, threadFactory, handler);
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.decorateTaskRunnable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "decorateTask", Runnable.class,
				RunnableScheduledFuture.class);
		makeAccessibleIfNotNull(this.decorateTaskRunnable);
		this.decorateTaskCallable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "decorateTaskCallable", Callable.class,
				RunnableScheduledFuture.class);
		makeAccessibleIfNotNull(this.decorateTaskCallable);
		this.finalize = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"finalize", null);
		makeAccessibleIfNotNull(this.finalize);
		this.beforeExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"beforeExecute", null);
		makeAccessibleIfNotNull(this.beforeExecute);
		this.afterExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"afterExecute", null);
		makeAccessibleIfNotNull(this.afterExecute);
		this.terminated = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class,
				"terminated");
		makeAccessibleIfNotNull(this.terminated);
		this.newTaskForRunnable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "newTaskFor", Runnable.class,
				Object.class);
		makeAccessibleIfNotNull(this.newTaskForRunnable);
		this.newTaskForCallable = ReflectionUtils.findMethod(
				ScheduledThreadPoolExecutor.class, "newTaskFor", Callable.class,
				Object.class);
		makeAccessibleIfNotNull(this.newTaskForCallable);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <V> RunnableScheduledFuture<V> decorateTask(Runnable runnable,
			RunnableScheduledFuture<V> task) {
		return (RunnableScheduledFuture<V>) ReflectionUtils.invokeMethod(
				this.decorateTaskRunnable, this.delegate,
				new TraceRunnable(tracing(), spanNamer(), runnable), task);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable,
			RunnableScheduledFuture<V> task) {
		return (RunnableScheduledFuture<V>) ReflectionUtils.invokeMethod(
				this.decorateTaskCallable, this.delegate,
				new TraceCallable<>(tracing(), spanNamer(), callable), task);
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		return this.delegate.schedule(new TraceRunnable(tracing(), spanNamer(), command),
				delay, unit);
	}

	@Override
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay,
			TimeUnit unit) {
		return this.delegate.schedule(
				new TraceCallable<>(tracing(), spanNamer(), callable), delay, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
			long period, TimeUnit unit) {
		return this.delegate.scheduleAtFixedRate(
				new TraceRunnable(tracing(), spanNamer(), command), initialDelay, period,
				unit);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
			long delay, TimeUnit unit) {
		return this.delegate.scheduleWithFixedDelay(
				new TraceRunnable(tracing(), spanNamer(), command), initialDelay, delay,
				unit);
	}

	@Override
	public void execute(Runnable command) {
		this.delegate.execute(new TraceRunnable(tracing(), spanNamer(), command));
	}

	@Override
	public Future<?> submit(Runnable task) {
		return this.delegate.submit(new TraceRunnable(tracing(), spanNamer(), task));
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		return this.delegate.submit(new TraceRunnable(tracing(), spanNamer(), task),
				result);
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return this.delegate.submit(new TraceCallable<>(tracing(), spanNamer(), task));
	}

	@Override
	public void setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean value) {
		this.delegate.setContinueExistingPeriodicTasksAfterShutdownPolicy(value);
	}

	@Override
	public boolean getContinueExistingPeriodicTasksAfterShutdownPolicy() {
		return this.delegate.getContinueExistingPeriodicTasksAfterShutdownPolicy();
	}

	@Override
	public void setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean value) {
		this.delegate.setExecuteExistingDelayedTasksAfterShutdownPolicy(value);
	}

	@Override
	public boolean getExecuteExistingDelayedTasksAfterShutdownPolicy() {
		return this.delegate.getExecuteExistingDelayedTasksAfterShutdownPolicy();
	}

	@Override
	public void setRemoveOnCancelPolicy(boolean value) {
		this.delegate.setRemoveOnCancelPolicy(value);
	}

	@Override
	public boolean getRemoveOnCancelPolicy() {
		return this.delegate.getRemoveOnCancelPolicy();
	}

	@Override
	public void shutdown() {
		this.delegate.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		return this.delegate.shutdownNow();
	}

	@Override
	public BlockingQueue<Runnable> getQueue() {
		return this.delegate.getQueue();
	}

	@Override
	public boolean isShutdown() {
		return this.delegate.isShutdown();
	}

	@Override
	public boolean isTerminating() {
		return this.delegate.isTerminating();
	}

	@Override
	public boolean isTerminated() {
		return this.delegate.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit)
			throws InterruptedException {
		return this.delegate.awaitTermination(timeout, unit);
	}

	@Override
	public void finalize() {
	}

	@Override
	public void setThreadFactory(ThreadFactory threadFactory) {
		this.delegate.setThreadFactory(threadFactory);
	}

	@Override
	public ThreadFactory getThreadFactory() {
		return this.delegate.getThreadFactory();
	}

	@Override
	public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
		this.delegate.setRejectedExecutionHandler(handler);
	}

	@Override
	public RejectedExecutionHandler getRejectedExecutionHandler() {
		return this.delegate.getRejectedExecutionHandler();
	}

	@Override
	public void setCorePoolSize(int corePoolSize) {
		this.delegate.setCorePoolSize(corePoolSize);
	}

	@Override
	public int getCorePoolSize() {
		return this.delegate.getCorePoolSize();
	}

	@Override
	public boolean prestartCoreThread() {
		return this.delegate.prestartCoreThread();
	}

	@Override
	public int prestartAllCoreThreads() {
		return this.delegate.prestartAllCoreThreads();
	}

	@Override
	public boolean allowsCoreThreadTimeOut() {
		return this.delegate.allowsCoreThreadTimeOut();
	}

	@Override
	public void allowCoreThreadTimeOut(boolean value) {
		this.delegate.allowCoreThreadTimeOut(value);
	}

	@Override
	public void setMaximumPoolSize(int maximumPoolSize) {
		this.delegate.setMaximumPoolSize(maximumPoolSize);
	}

	@Override
	public int getMaximumPoolSize() {
		return this.delegate.getMaximumPoolSize();
	}

	@Override
	public void setKeepAliveTime(long time, TimeUnit unit) {
		this.delegate.setKeepAliveTime(time, unit);
	}

	@Override
	public long getKeepAliveTime(TimeUnit unit) {
		return this.delegate.getKeepAliveTime(unit);
	}

	@Override
	public boolean remove(Runnable task) {
		return this.delegate.remove(task);
	}

	@Override
	public void purge() {
		this.delegate.purge();
	}

	@Override
	public int getPoolSize() {
		return this.delegate.getPoolSize();
	}

	@Override
	public int getActiveCount() {
		return this.delegate.getActiveCount();
	}

	@Override
	public int getLargestPoolSize() {
		return this.delegate.getLargestPoolSize();
	}

	@Override
	public long getTaskCount() {
		return this.delegate.getTaskCount();
	}

	@Override
	public long getCompletedTaskCount() {
		return this.delegate.getCompletedTaskCount();
	}

	@Override
	public String toString() {
		return this.delegate.toString();
	}

	@Override
	public void beforeExecute(Thread t, Runnable r) {
		ReflectionUtils.invokeMethod(this.beforeExecute, this.delegate, t,
				new TraceRunnable(tracing(), spanNamer(), r));
	}

	@Override
	public void afterExecute(Runnable r, Throwable t) {
		ReflectionUtils.invokeMethod(this.afterExecute, this.delegate,
				new TraceRunnable(tracing(), spanNamer(), r), t);
	}

	@Override
	public void terminated() {
		ReflectionUtils.invokeMethod(this.terminated, this.delegate);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
		return (RunnableFuture<T>) ReflectionUtils.invokeMethod(this.newTaskForRunnable,
				this.delegate, new TraceRunnable(tracing(), spanNamer(), runnable),
				value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
		return (RunnableFuture<T>) ReflectionUtils.invokeMethod(this.newTaskForCallable,
				this.delegate, new TraceCallable<>(tracing(), spanNamer(), callable));
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
			throws InterruptedException, ExecutionException {
		return this.delegate.invokeAny(wrapCallableCollection(tasks));
	}

	private <T> Collection<? extends Callable<T>> wrapCallableCollection(
			Collection<? extends Callable<T>> tasks) {
		List<Callable<T>> ts = new ArrayList<>();
		for (Callable<T> task : tasks) {
			if (!(task instanceof TraceCallable)) {
				ts.add(new TraceCallable<>(tracing(), spanNamer(), task));
			}
		}
		return ts;
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout,
			TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		return this.delegate.invokeAny(wrapCallableCollection(tasks), timeout, unit);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
			throws InterruptedException {
		return this.delegate.invokeAll(wrapCallableCollection(tasks));
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
			long timeout, TimeUnit unit) throws InterruptedException {
		return this.delegate.invokeAll(wrapCallableCollection(tasks), timeout, unit);
	}

	private Tracing tracing() {
		if (this.tracing == null) {
			this.tracing = this.beanFactory.getBean(Tracing.class);
		}
		return this.tracing;
	}

	private SpanNamer spanNamer() {
		if (this.spanNamer == null) {
			try {
				this.spanNamer = this.beanFactory.getBean(SpanNamer.class);
			}
			catch (NoSuchBeanDefinitionException e) {
				log.warn(
						"SpanNamer bean not found - will provide a manually created instance");
				return new DefaultSpanNamer();
			}
		}
		return this.spanNamer;
	}

}
