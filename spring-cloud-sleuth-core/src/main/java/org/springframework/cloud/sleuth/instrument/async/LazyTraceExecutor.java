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

import java.util.concurrent.Executor;

import brave.Tracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.ExceptionMessageErrorParser;
import org.springframework.cloud.sleuth.SpanNamer;

/**
 * {@link Executor} that wraps {@link Runnable} in a trace representation
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class LazyTraceExecutor implements Executor {

	private static final Log log = LogFactory.getLog(LazyTraceExecutor.class);

	private Tracer tracer;
	private final BeanFactory beanFactory;
	private final Executor delegate;
	private SpanNamer spanNamer;
	private ErrorParser errorParser;

	public LazyTraceExecutor(BeanFactory beanFactory, Executor delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override
	public void execute(Runnable command) {
		if (this.tracer == null) {
			try {
				this.tracer = this.beanFactory.getBean(Tracer.class);
			}
			catch (NoSuchBeanDefinitionException e) {
				this.delegate.execute(command);
				return;
			}
		}
		this.delegate.execute(new TraceRunnable(this.tracer, spanNamer(), errorParser(), command));
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

	// due to some race conditions trace keys might not be ready yet
	private ErrorParser errorParser() {
		if (this.errorParser == null) {
			try {
				this.errorParser = this.beanFactory.getBean(ErrorParser.class);
			}
			catch (NoSuchBeanDefinitionException e) {
				log.warn("ErrorParser bean not found - will provide a manually created instance");
				return new ExceptionMessageErrorParser();
			}
		}
		return this.errorParser;
	}

}
