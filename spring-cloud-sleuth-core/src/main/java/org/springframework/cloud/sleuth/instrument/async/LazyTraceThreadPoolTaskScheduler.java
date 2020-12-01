/*
 * Copyright 2013-2019 the original author or authors.
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
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import brave.Tracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.internal.ContextUtil;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.CustomizableThreadCreator;
import org.springframework.util.ErrorHandler;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * Trace representation of {@link ThreadPoolTaskScheduler}. Should be used only as last
 * resort, when any other approaches fail.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.4
 */
// TODO: Think of a better solution than this
class LazyTraceThreadPoolTaskScheduler extends ThreadPoolTaskScheduler {

	private static final Log log = LogFactory
			.getLog(LazyTraceThreadPoolTaskScheduler.class);

	private final BeanFactory beanFactory;

	private final ThreadPoolTaskScheduler delegate;

	private final Method initializeExecutor;

	private final Method createExecutor;

	private final Method cancelRemainingTask;

	private final Method nextThreadName;

	private final Method getDefaultThreadNamePrefix;

	private Tracing tracing;

	private SpanNamer spanNamer;

	LazyTraceThreadPoolTaskScheduler(BeanFactory beanFactory,
			ThreadPoolTaskScheduler delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.initializeExecutor = ReflectionUtils
				.findMethod(ThreadPoolTaskScheduler.class, "initializeExecutor", null);
		makeAccessibleIfNotNull(this.initializeExecutor);
		this.createExecutor = ReflectionUtils.findMethod(ThreadPoolTaskScheduler.class,
				"createExecutor", null);
		makeAccessibleIfNotNull(this.createExecutor);
		this.cancelRemainingTask = ReflectionUtils
				.findMethod(ThreadPoolTaskScheduler.class, "cancelRemainingTask", null);
		makeAccessibleIfNotNull(this.cancelRemainingTask);
		this.nextThreadName = ReflectionUtils.findMethod(ThreadPoolTaskScheduler.class,
				"nextThreadName", null);
		makeAccessibleIfNotNull(this.nextThreadName);
		this.getDefaultThreadNamePrefix = ReflectionUtils.findMethod(
				CustomizableThreadCreator.class, "getDefaultThreadNamePrefix", null);
		makeAccessibleIfNotNull(this.getDefaultThreadNamePrefix);
	}

	private void makeAccessibleIfNotNull(Method method) {
		if (method != null) {
			ReflectionUtils.makeAccessible(method);
		}
	}

	private Runnable traceRunnableWhenContextReady(Runnable delegate) {
		if (ContextUtil.isContextUnusable(this.beanFactory)) {
			return delegate;
		}
		return new TraceRunnable(tracing(), spanNamer(), delegate);
	}

	private <V> Callable<V> traceCallableWhenContextReady(Callable<V> delegate) {
		if (ContextUtil.isContextUnusable(this.beanFactory)) {
			return delegate;
		}
		return new TraceCallable<>(tracing(), spanNamer(), delegate);
	}

	@Override
	public void setPoolSize(int poolSize) {
		this.delegate.setPoolSize(poolSize);
	}

	@Override
	public void setRemoveOnCancelPolicy(boolean removeOnCancelPolicy) {
		this.delegate.setRemoveOnCancelPolicy(removeOnCancelPolicy);
	}

	@Override
	public void setErrorHandler(ErrorHandler errorHandler) {
		this.delegate.setErrorHandler(errorHandler);
	}

	@Override
	public ExecutorService initializeExecutor(ThreadFactory threadFactory,
			RejectedExecutionHandler rejectedExecutionHandler) {
		ExecutorService executorService = (ExecutorService) ReflectionUtils.invokeMethod(
				this.initializeExecutor, this.delegate, traceThreadFactory(threadFactory),
				rejectedExecutionHandler);
		if (executorService instanceof TraceableScheduledExecutorService) {
			return executorService;
		}
		return new TraceableExecutorService(this.beanFactory, executorService);
	}

	private ThreadFactory traceThreadFactory(ThreadFactory threadFactory) {
		return r -> threadFactory.newThread(new TraceRunnable(tracing(), spanNamer(), r));
	}

	@Override
	public ScheduledExecutorService createExecutor(int poolSize,
			ThreadFactory threadFactory,
			RejectedExecutionHandler rejectedExecutionHandler) {
		ScheduledExecutorService executorService = (ScheduledExecutorService) ReflectionUtils
				.invokeMethod(this.createExecutor, this.delegate, poolSize,
						traceThreadFactory(threadFactory), rejectedExecutionHandler);
		if (executorService instanceof TraceableScheduledExecutorService) {
			return executorService;
		}
		return new TraceableScheduledExecutorService(this.beanFactory, executorService);
	}

	@Override
	public ScheduledExecutorService getScheduledExecutor() throws IllegalStateException {
		ScheduledExecutorService executor = this.delegate.getScheduledExecutor();
		return executor instanceof TraceableScheduledExecutorService ? executor
				: new TraceableScheduledExecutorService(this.beanFactory, executor);
	}

	@Override
	public ScheduledThreadPoolExecutor getScheduledThreadPoolExecutor()
			throws IllegalStateException {
		ScheduledThreadPoolExecutor executor = this.delegate
				.getScheduledThreadPoolExecutor();
		if (executor instanceof LazyTraceScheduledThreadPoolExecutor) {
			return executor;
		}
		return new LazyTraceScheduledThreadPoolExecutor(executor.getCorePoolSize(),
				executor.getThreadFactory(), executor.getRejectedExecutionHandler(),
				this.beanFactory, executor);
	}

	@Override
	public int getPoolSize() {
		return this.delegate.getPoolSize();
	}

	@Override
	public boolean isRemoveOnCancelPolicy() {
		return this.delegate.isRemoveOnCancelPolicy();
	}

	@Override
	public int getActiveCount() {
		return this.delegate.getActiveCount();
	}

	@Override
	public void execute(Runnable task) {
		this.delegate.execute(traceRunnableWhenContextReady(task));
	}

	@Override
	public void execute(Runnable task, long startTimeout) {
		this.delegate.execute(traceRunnableWhenContextReady(task), startTimeout);
	}

	@Override
	public Future<?> submit(Runnable task) {
		return this.delegate.submit(traceRunnableWhenContextReady(task));
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return this.delegate.submit(traceCallableWhenContextReady(task));
	}

	@Override
	public ListenableFuture<?> submitListenable(Runnable task) {
		return this.delegate.submitListenable(traceRunnableWhenContextReady(task));
	}

	@Override
	public <T> ListenableFuture<T> submitListenable(Callable<T> task) {
		return this.delegate.submitListenable(traceCallableWhenContextReady(task));
	}

	@Override
	public void cancelRemainingTask(Runnable task) {
		ReflectionUtils.invokeMethod(this.cancelRemainingTask, this.delegate,
				traceRunnableWhenContextReady(task));
	}

	@Override
	public boolean prefersShortLivedTasks() {
		return this.delegate.prefersShortLivedTasks();
	}

	@Override
	@Nullable
	public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
		return this.delegate.schedule(traceRunnableWhenContextReady(task), trigger);
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable task, Date startTime) {
		return this.delegate.schedule(traceRunnableWhenContextReady(task), startTime);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Date startTime,
			long period) {
		return this.delegate.scheduleAtFixedRate(traceRunnableWhenContextReady(task),
				startTime, period);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long period) {
		return this.delegate.scheduleAtFixedRate(traceRunnableWhenContextReady(task),
				period);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Date startTime,
			long delay) {
		return this.delegate.scheduleWithFixedDelay(traceRunnableWhenContextReady(task),
				startTime, delay);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay) {
		return this.delegate.scheduleWithFixedDelay(traceRunnableWhenContextReady(task),
				delay);
	}

	@Override
	public void setThreadFactory(ThreadFactory threadFactory) {
		this.delegate.setThreadFactory(threadFactory);
	}

	@Override
	public void setThreadNamePrefix(String threadNamePrefix) {
		this.delegate.setThreadNamePrefix(threadNamePrefix);
	}

	@Override
	public void setRejectedExecutionHandler(
			RejectedExecutionHandler rejectedExecutionHandler) {
		this.delegate.setRejectedExecutionHandler(rejectedExecutionHandler);
	}

	@Override
	public void setWaitForTasksToCompleteOnShutdown(
			boolean waitForJobsToCompleteOnShutdown) {
		this.delegate
				.setWaitForTasksToCompleteOnShutdown(waitForJobsToCompleteOnShutdown);
	}

	@Override
	public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
		this.delegate.setAwaitTerminationSeconds(awaitTerminationSeconds);
	}

	@Override
	public void setBeanName(String name) {
		this.delegate.setBeanName(name);
	}

	@Override
	public void afterPropertiesSet() {
		this.delegate.afterPropertiesSet();
	}

	@Override
	public void initialize() {
		this.delegate.initialize();
	}

	@Override
	public void destroy() {
		this.delegate.destroy();
	}

	@Override
	public void shutdown() {
		this.delegate.shutdown();
	}

	@Override
	public Thread newThread(Runnable runnable) {
		return this.delegate.newThread(runnable);
	}

	@Override
	public String getThreadNamePrefix() {
		return this.delegate.getThreadNamePrefix();
	}

	@Override
	public void setThreadPriority(int threadPriority) {
		this.delegate.setThreadPriority(threadPriority);
	}

	@Override
	public int getThreadPriority() {
		return this.delegate.getThreadPriority();
	}

	@Override
	public void setDaemon(boolean daemon) {
		this.delegate.setDaemon(daemon);
	}

	@Override
	public boolean isDaemon() {
		return this.delegate.isDaemon();
	}

	@Override
	public void setThreadGroupName(String name) {
		this.delegate.setThreadGroupName(name);
	}

	@Override
	public void setThreadGroup(ThreadGroup threadGroup) {
		this.delegate.setThreadGroup(threadGroup);
	}

	@Override
	@Nullable
	public ThreadGroup getThreadGroup() {
		return this.delegate.getThreadGroup();
	}

	@Override
	public Thread createThread(Runnable runnable) {
		return this.delegate.createThread(runnable);
	}

	@Override
	public String nextThreadName() {
		return (String) ReflectionUtils.invokeMethod(this.nextThreadName, this.delegate);
	}

	@Override
	public String getDefaultThreadNamePrefix() {
		if (this.delegate == null) {
			return super.getDefaultThreadNamePrefix();
		}
		return (String) ReflectionUtils.invokeMethod(this.getDefaultThreadNamePrefix,
				this.delegate);
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
		return this.delegate.schedule(traceRunnableWhenContextReady(task), startTime);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime,
			Duration period) {
		return this.delegate.scheduleAtFixedRate(traceRunnableWhenContextReady(task),
				startTime, period);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
		return this.delegate.scheduleAtFixedRate(traceRunnableWhenContextReady(task),
				period);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime,
			Duration delay) {
		return this.delegate.scheduleWithFixedDelay(traceRunnableWhenContextReady(task),
				startTime, delay);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
		return this.delegate.scheduleWithFixedDelay(traceRunnableWhenContextReady(task),
				delay);
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
