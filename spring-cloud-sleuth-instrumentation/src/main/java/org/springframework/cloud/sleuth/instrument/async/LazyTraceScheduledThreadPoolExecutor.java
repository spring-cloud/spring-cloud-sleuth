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
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.ContextUtil;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.lang.NonNull;
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

	private static final Log log = LogFactory.getLog(LazyTraceScheduledThreadPoolExecutor.class);

	private static final Map<ScheduledThreadPoolExecutor, LazyTraceScheduledThreadPoolExecutor> CACHE = new ConcurrentHashMap<>();

	private final BeanFactory beanFactory;

	private final ScheduledThreadPoolExecutor delegate;

	private final String beanName;

	private final Method decorateTaskRunnable;

	private final Method decorateTaskCallable;

	private final Method beforeExecute;

	private final Method afterExecute;

	private final Method terminated;

	private final Method newTaskForRunnable;

	private final Method newTaskForCallable;

	private Tracer tracing;

	private SpanNamer spanNamer;

	LazyTraceScheduledThreadPoolExecutor(int corePoolSize, BeanFactory beanFactory,
			ScheduledThreadPoolExecutor delegate, String beanName) {
		super(corePoolSize);
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.beanName = beanName;
		Method decorateTaskRunnable = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "decorateTask",
				Runnable.class, RunnableScheduledFuture.class);
		this.decorateTaskRunnable = makeAccessibleIfNotNullAndOverridden(decorateTaskRunnable);
		Method decorateTaskCallable = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "decorateTask",
				Callable.class, RunnableScheduledFuture.class);
		this.decorateTaskCallable = makeAccessibleIfNotNullAndOverridden(decorateTaskCallable);
		Method beforeExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "beforeExecute", null);
		this.beforeExecute = makeAccessibleIfNotNullAndOverridden(beforeExecute);
		Method afterExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "afterExecute", null);
		this.afterExecute = makeAccessibleIfNotNullAndOverridden(afterExecute);
		Method terminated = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "terminated", null);
		this.terminated = makeAccessibleIfNotNullAndOverridden(terminated);
		Method newTaskForRunnable = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "newTaskFor",
				Runnable.class, Object.class);
		this.newTaskForRunnable = makeAccessibleIfNotNullAndOverridden(newTaskForRunnable);
		Method newTaskForCallable = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "newTaskFor",
				Callable.class);
		this.newTaskForCallable = makeAccessibleIfNotNullAndOverridden(newTaskForCallable);
	}

	private Method makeAccessibleIfNotNullAndOverridden(Method method) {
		if (method != null) {
			if (isMethodOverridden(method)) {
				try {
					ReflectionUtils.makeAccessible(method);
					return method;
				}
				catch (Throwable ex) {
					if (anyCauseIsInaccessibleObjectException(ex)) {
						throw new IllegalStateException("The executor [" + this.delegate.getClass()
								+ "] has overridden a method with name [" + method.getName()
								+ "] and the object is inaccessible. You have to run your JVM with [--add-opens] switch to allow such access. Example: [--add-opens java.base/java.util.concurrent=ALL-UNNAMED].",
								ex);
					}
					throw ex;
				}
			}
		}
		return null;
	}

	private boolean anyCauseIsInaccessibleObjectException(Throwable t) {
		Throwable parent = t;
		Throwable cause = t.getCause();
		while (cause != null && cause != parent) {
			if (cause.getClass().toString().contains("InaccessibleObjectException")) {
				return true;
			}
			parent = cause;
			cause = parent.getCause();
		}
		return false;
	}

	boolean isMethodOverridden(Method originalMethod) {
		Method delegateMethod = ReflectionUtils.findMethod(this.delegate.getClass(), originalMethod.getName());
		if (delegateMethod == null) {
			return false;
		}
		return !delegateMethod.equals(originalMethod);
	}

	LazyTraceScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory,
			RejectedExecutionHandler handler, BeanFactory beanFactory, ScheduledThreadPoolExecutor delegate,
			String beanName) {
		super(corePoolSize, threadFactory, handler);
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.beanName = beanName;
		Method decorateTaskRunnable = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "decorateTask",
				Runnable.class, RunnableScheduledFuture.class);
		this.decorateTaskRunnable = makeAccessibleIfNotNullAndOverridden(decorateTaskRunnable);
		Method decorateTaskCallable = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "decorateTask",
				Callable.class, RunnableScheduledFuture.class);
		this.decorateTaskCallable = makeAccessibleIfNotNullAndOverridden(decorateTaskCallable);
		Method beforeExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "beforeExecute", null);
		this.beforeExecute = makeAccessibleIfNotNullAndOverridden(beforeExecute);
		Method afterExecute = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "afterExecute", null);
		this.afterExecute = makeAccessibleIfNotNullAndOverridden(afterExecute);
		Method terminated = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "terminated", null);
		this.terminated = makeAccessibleIfNotNullAndOverridden(terminated);
		Method newTaskForRunnable = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "newTaskFor",
				Runnable.class, Object.class);
		this.newTaskForRunnable = makeAccessibleIfNotNullAndOverridden(newTaskForRunnable);
		Method newTaskForCallable = ReflectionUtils.findMethod(ScheduledThreadPoolExecutor.class, "newTaskFor",
				Callable.class);
		this.newTaskForCallable = makeAccessibleIfNotNullAndOverridden(newTaskForCallable);
	}

	static LazyTraceScheduledThreadPoolExecutor wrap(int corePoolSize, BeanFactory beanFactory,
			@NonNull ScheduledThreadPoolExecutor delegate, String beanName) {
		return CACHE.computeIfAbsent(delegate,
				e -> new LazyTraceScheduledThreadPoolExecutor(corePoolSize, beanFactory, delegate, beanName));
	}

	static LazyTraceScheduledThreadPoolExecutor wrap(int corePoolSize, ThreadFactory threadFactory,
			RejectedExecutionHandler handler, BeanFactory beanFactory, @NonNull ScheduledThreadPoolExecutor delegate,
			String beanName) {
		return CACHE.computeIfAbsent(delegate, e -> new LazyTraceScheduledThreadPoolExecutor(corePoolSize,
				threadFactory, handler, beanFactory, delegate, beanName));
	}

	private Runnable traceRunnableWhenContextReady(Runnable delegate) {
		if (isContextUnusable()) {
			return delegate;
		}
		return new TraceRunnable(tracing(), spanNamer(), delegate, this.beanName);
	}

	boolean isContextUnusable() {
		return ContextUtil.isContextUnusable(this.beanFactory);
	}

	private <V> Callable<V> traceCallableWhenContextReady(Callable<V> delegate) {
		if (isContextUnusable()) {
			return delegate;
		}
		return new TraceCallable<>(tracing(), spanNamer(), delegate, this.beanName);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <V> RunnableScheduledFuture<V> decorateTask(Runnable runnable, RunnableScheduledFuture<V> task) {
		if (this.decorateTaskRunnable == null) {
			return super.decorateTask(traceRunnableWhenContextReady(runnable), task);
		}
		return (RunnableScheduledFuture<V>) ReflectionUtils.invokeMethod(this.decorateTaskRunnable, this.delegate,
				traceRunnableWhenContextReady(runnable), task);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable, RunnableScheduledFuture<V> task) {
		if (this.decorateTaskCallable == null) {
			return super.decorateTask(traceCallableWhenContextReady(callable), task);
		}
		return (RunnableScheduledFuture<V>) ReflectionUtils.invokeMethod(this.decorateTaskCallable, this.delegate,
				traceCallableWhenContextReady(callable), task);
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		return this.delegate.schedule(traceRunnableWhenContextReady(command), delay, unit);
	}

	@Override
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
		return this.delegate.schedule(traceCallableWhenContextReady(callable), delay, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
		return this.delegate.scheduleAtFixedRate(traceRunnableWhenContextReady(command), initialDelay, period, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
		return this.delegate.scheduleWithFixedDelay(traceRunnableWhenContextReady(command), initialDelay, delay, unit);
	}

	@Override
	public void execute(Runnable command) {
		this.delegate.execute(traceRunnableWhenContextReady(command));
	}

	@Override
	public Future<?> submit(Runnable task) {
		return this.delegate.submit(traceRunnableWhenContextReady(task));
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		return this.delegate.submit(traceRunnableWhenContextReady(task), result);
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return this.delegate.submit(traceCallableWhenContextReady(task));
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
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
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
		return this.delegate.remove(traceRunnableWhenContextReady(task));
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
		if (this.beforeExecute == null) {
			super.beforeExecute(t, traceRunnableWhenContextReady(r));
			return;
		}
		ReflectionUtils.invokeMethod(this.beforeExecute, this.delegate, t, traceRunnableWhenContextReady(r));
	}

	@Override
	public void afterExecute(Runnable r, Throwable t) {
		if (this.afterExecute == null) {
			super.afterExecute(traceRunnableWhenContextReady(r), t);
			return;
		}
		ReflectionUtils.invokeMethod(this.afterExecute, this.delegate, traceRunnableWhenContextReady(r), t);
	}

	@Override
	public void terminated() {
		if (this.terminated == null) {
			super.terminated();
			return;
		}
		ReflectionUtils.invokeMethod(this.terminated, this.delegate);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
		if (this.newTaskForRunnable == null) {
			return super.newTaskFor(traceRunnableWhenContextReady(runnable), value);
		}
		return (RunnableFuture<T>) ReflectionUtils.invokeMethod(this.newTaskForRunnable, this.delegate,
				traceRunnableWhenContextReady(runnable), value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
		if (this.newTaskForRunnable == null) {
			return super.newTaskFor(traceCallableWhenContextReady(callable));
		}
		return (RunnableFuture<T>) ReflectionUtils.invokeMethod(this.newTaskForCallable, this.delegate,
				traceCallableWhenContextReady(callable));
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		return this.delegate.invokeAny(wrapCallableCollection(tasks));
	}

	<T> Collection<? extends Callable<T>> wrapCallableCollection(Collection<? extends Callable<T>> tasks) {
		List<Callable<T>> ts = new ArrayList<>();
		for (Callable<T> task : tasks) {
			if (!(task instanceof TraceCallable)) {
				ts.add(traceCallableWhenContextReady(task));
			}
		}
		return ts;
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		return this.delegate.invokeAny(wrapCallableCollection(tasks), timeout, unit);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		return this.delegate.invokeAll(wrapCallableCollection(tasks));
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException {
		return this.delegate.invokeAll(wrapCallableCollection(tasks), timeout, unit);
	}

	private Tracer tracing() {
		if (this.tracing == null) {
			this.tracing = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracing;
	}

	private SpanNamer spanNamer() {
		if (this.spanNamer == null) {
			try {
				this.spanNamer = this.beanFactory.getBean(SpanNamer.class);
			}
			catch (NoSuchBeanDefinitionException e) {
				log.warn("SpanNamer bean not found - will provide a manually created instance");
				return new DefaultSpanNamer();
			}
		}
		return this.spanNamer;
	}

}
