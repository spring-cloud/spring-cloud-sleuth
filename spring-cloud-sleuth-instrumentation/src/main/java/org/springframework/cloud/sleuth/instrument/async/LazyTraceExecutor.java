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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.ContextUtil;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.lang.NonNull;

/**
 * {@link Executor} that wraps {@link Runnable} in a trace representation.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
// public as most types in this package were documented for use
public class LazyTraceExecutor implements Executor {

	private static final Log log = LogFactory.getLog(LazyTraceExecutor.class);

	private static final Map<Executor, LazyTraceExecutor> CACHE = new ConcurrentHashMap<>();

	private final BeanFactory beanFactory;

	private final Executor delegate;

	private final String beanName;

	private Tracer tracer;

	private SpanNamer spanNamer;

	public LazyTraceExecutor(BeanFactory beanFactory, Executor delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.beanName = null;
	}

	public LazyTraceExecutor(BeanFactory beanFactory, Executor delegate, String beanName) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.beanName = beanName;
	}

	/**
	 * Wraps the Executor in a trace instance.
	 * @param beanFactory bean factory
	 * @param delegate delegate to wrap
	 * @param beanName bean name
	 * @return traced instance
	 */
	public static LazyTraceExecutor wrap(BeanFactory beanFactory, @NonNull Executor delegate, String beanName) {
		return CACHE.computeIfAbsent(delegate, e -> new LazyTraceExecutor(beanFactory, delegate, beanName));
	}

	/**
	 * Wraps the Executor in a trace instance.
	 * @param beanFactory bean factory
	 * @param delegate delegate to wrap
	 * @return traced instance
	 */
	public static LazyTraceExecutor wrap(BeanFactory beanFactory, @NonNull Executor delegate) {
		return CACHE.computeIfAbsent(delegate, e -> new LazyTraceExecutor(beanFactory, delegate, null));
	}

	@Override
	public void execute(Runnable command) {
		if (ContextUtil.isContextUnusable(this.beanFactory)) {
			this.delegate.execute(command);
			return;
		}
		if (this.tracer == null) {
			try {
				this.tracer = this.beanFactory.getBean(Tracer.class);
			}
			catch (NoSuchBeanDefinitionException e) {
				this.delegate.execute(command);
				return;
			}
		}
		this.delegate.execute(new TraceRunnable(this.tracer, spanNamer(), command, this.beanName));
	}

	// due to some race conditions trace keys might not be ready yet
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
