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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import brave.Tracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * Trace representation of {@link ThreadPoolTaskExecutor}
 *
 * @author Marcin Grzejszczak
 * @since 1.0.10
 */
@SuppressWarnings("serial")
public class LazyTraceThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {

	private static final Log log = LogFactory.getLog(LazyTraceThreadPoolTaskExecutor.class);

	private final BeanFactory beanFactory;
	private final ThreadPoolTaskExecutor delegate;
	private Tracing tracing;
	private SpanNamer spanNamer;

	public LazyTraceThreadPoolTaskExecutor(BeanFactory beanFactory,
			ThreadPoolTaskExecutor delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override
	public void execute(Runnable task) {
		this.delegate.execute(new TraceRunnable(tracing(), spanNamer(), task));
	}

	@Override
	public void execute(Runnable task, long startTimeout) {
		this.delegate.execute(new TraceRunnable(tracing(), spanNamer(), task), startTimeout);
	}

	@Override
	public Future<?> submit(Runnable task) {
		return this.delegate.submit(new TraceRunnable(tracing(), spanNamer(), task));
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return this.delegate.submit(new TraceCallable<>(tracing(), spanNamer(), task));
	}

	@Override
	public ListenableFuture<?> submitListenable(Runnable task) {
		return this.delegate.submitListenable(new TraceRunnable(tracing(), spanNamer(), task));
	}

	@Override
	public <T> ListenableFuture<T> submitListenable(Callable<T> task) {
		return this.delegate.submitListenable(new TraceCallable<>(tracing(), spanNamer(), task));
	}

	@Override public boolean prefersShortLivedTasks() {
		return this.delegate.prefersShortLivedTasks();
	}

	@Override public void setThreadFactory(ThreadFactory threadFactory) {
		this.delegate.setThreadFactory(threadFactory);
	}

	@Override public void setThreadNamePrefix(String threadNamePrefix) {
		this.delegate.setThreadNamePrefix(threadNamePrefix);
	}

	@Override public void setRejectedExecutionHandler(
			RejectedExecutionHandler rejectedExecutionHandler) {
		this.delegate.setRejectedExecutionHandler(rejectedExecutionHandler);
	}

	@Override public void setWaitForTasksToCompleteOnShutdown(
			boolean waitForJobsToCompleteOnShutdown) {
		this.delegate.setWaitForTasksToCompleteOnShutdown(waitForJobsToCompleteOnShutdown);
	}

	@Override public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
		this.delegate.setAwaitTerminationSeconds(awaitTerminationSeconds);
	}

	@Override public void setBeanName(String name) {
		this.delegate.setBeanName(name);
	}

	@Override
	public ThreadPoolExecutor getThreadPoolExecutor() throws IllegalStateException {
		return this.delegate.getThreadPoolExecutor();
	}

	@Override public int getPoolSize() {
		return this.delegate.getPoolSize();
	}

	@Override public int getActiveCount() {
		return this.delegate.getActiveCount();
	}

	@Override
	public void destroy() {
		this.delegate.destroy();
		super.destroy();
	}

	@Override
	public void afterPropertiesSet() {
		this.delegate.afterPropertiesSet();
		super.afterPropertiesSet();
	}

	@Override public void initialize() {
		this.delegate.initialize();
	}

	@Override
	public void shutdown() {
		this.delegate.shutdown();
		super.shutdown();
	}

	@Override public Thread newThread(Runnable runnable) {
		return this.delegate.newThread(runnable);
	}

	@Override public String getThreadNamePrefix() {
		return this.delegate.getThreadNamePrefix();
	}

	@Override public void setThreadPriority(int threadPriority) {
		this.delegate.setThreadPriority(threadPriority);
	}

	@Override public int getThreadPriority() {
		return this.delegate.getThreadPriority();
	}

	@Override public void setDaemon(boolean daemon) {
		this.delegate.setDaemon(daemon);
	}

	@Override public boolean isDaemon() {
		return this.delegate.isDaemon();
	}

	@Override public void setThreadGroupName(String name) {
		this.delegate.setThreadGroupName(name);
	}

	@Override public void setThreadGroup(ThreadGroup threadGroup) {
		this.delegate.setThreadGroup(threadGroup);
	}

	@Override public ThreadGroup getThreadGroup() {
		return this.delegate.getThreadGroup();
	}

	@Override public Thread createThread(Runnable runnable) {
		return this.delegate.createThread(runnable);
	}

	@Override public void setCorePoolSize(int corePoolSize) {
		this.delegate.setCorePoolSize(corePoolSize);
	}

	@Override public int getCorePoolSize() {
		return this.delegate.getCorePoolSize();
	}

	@Override public void setMaxPoolSize(int maxPoolSize) {
		this.delegate.setMaxPoolSize(maxPoolSize);
	}

	@Override public int getMaxPoolSize() {
		return this.delegate.getMaxPoolSize();
	}

	@Override public void setKeepAliveSeconds(int keepAliveSeconds) {
		this.delegate.setKeepAliveSeconds(keepAliveSeconds);
	}

	@Override public int getKeepAliveSeconds() {
		return this.delegate.getKeepAliveSeconds();
	}

	@Override public void setQueueCapacity(int queueCapacity) {
		this.delegate.setQueueCapacity(queueCapacity);
	}

	@Override public void setAllowCoreThreadTimeOut(boolean allowCoreThreadTimeOut) {
		this.delegate.setAllowCoreThreadTimeOut(allowCoreThreadTimeOut);
	}

	@Override public void setTaskDecorator(TaskDecorator taskDecorator) {
		this.delegate.setTaskDecorator(taskDecorator);
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
				log.warn("SpanNamer bean not found - will provide a manually created instance");
				return new DefaultSpanNamer();
			}
		}
		return this.spanNamer;
	}
}
