/*
 * Copyright 2013-2015 the original author or authors.
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
import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;

/**
 * @author Dave Syer
 *
 */
public class LazyTraceExecutor implements Executor {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private Tracer tracer;
	private final BeanFactory beanFactory;
	private final Executor delegate;
	private TraceKeys traceKeys;

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
			}
		}
		if (this.traceKeys == null) {
			try {
				this.traceKeys = this.beanFactory.getBean(TraceKeys.class);
			}
			catch (NoSuchBeanDefinitionException e) {
				log.warn("TraceKeys bean not found - will provide a manually created instance");
				this.delegate.execute(new LocalComponentTraceRunnable(this.tracer, new TraceKeys(), command));
				return;
			}
		}
		this.delegate.execute(new LocalComponentTraceRunnable(this.tracer, this.traceKeys, command));
	}

}
