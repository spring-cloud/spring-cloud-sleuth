/*
 * Copyright 2013-2017 the original author or authors.
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

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * {@link ThreadPoolTaskExecutor} that continues a span if one was passed or creates a new one
 *
 * @author Marcin Grzejszczak
 * @since 1.0.10
 */
@SuppressWarnings("serial")
public class LazyTraceThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private Tracer tracer;
	private final BeanFactory beanFactory;
	private final ThreadPoolTaskExecutor delegate;
	private TraceKeys traceKeys;
	private SpanNamer spanNamer;

	public LazyTraceThreadPoolTaskExecutor(BeanFactory beanFactory,
			ThreadPoolTaskExecutor delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override
	public void execute(Runnable task) {
		this.delegate.execute(new SpanContinuingTraceRunnable(tracer(), traceKeys(), spanNamer(), task));
	}

	@Override
	public void execute(Runnable task, long startTimeout) {
		this.delegate.execute(new SpanContinuingTraceRunnable(tracer(), traceKeys(), spanNamer(), task), startTimeout);
	}

	@Override
	public Future<?> submit(Runnable task) {
		return this.delegate.submit(new SpanContinuingTraceRunnable(tracer(), traceKeys(), spanNamer(), task));
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return this.delegate.submit(new SpanContinuingTraceCallable<>(tracer(), traceKeys(), spanNamer(), task));
	}

	@Override
	public ListenableFuture<?> submitListenable(Runnable task) {
		return this.delegate.submitListenable(new SpanContinuingTraceRunnable(tracer(), traceKeys(), spanNamer(), task));
	}

	@Override
	public <T> ListenableFuture<T> submitListenable(Callable<T> task) {
		return this.delegate.submitListenable(new SpanContinuingTraceCallable<>(tracer(), traceKeys(), spanNamer(), task));
	}

	@Override
	public ThreadPoolExecutor getThreadPoolExecutor() throws IllegalStateException {
		return this.delegate.getThreadPoolExecutor();
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

	@Override
	public void shutdown() {
		this.delegate.shutdown();
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	private TraceKeys traceKeys() {
		if (this.traceKeys == null) {
			try {
				this.traceKeys = this.beanFactory.getBean(TraceKeys.class);
			}
			catch (NoSuchBeanDefinitionException e) {
				log.warn("TraceKeys bean not found - will provide a manually created instance");
				return new TraceKeys();
			}
		}
		return this.traceKeys;
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
