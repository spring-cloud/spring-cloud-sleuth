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

import java.util.concurrent.Executor;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.instrument.TraceRunnable;

import lombok.RequiredArgsConstructor;

/**
 * @author Dave Syer
 *
 */
@RequiredArgsConstructor
public class LazyTraceExecutor implements Executor {

	private TraceManager traceManager;
	private final BeanFactory beanFactory;
	private final Executor delegate;

	@Override
	public void execute(Runnable command) {
		if (this.traceManager == null) {
			try {
				this.traceManager = this.beanFactory.getBean(TraceManager.class);
			}
			catch (NoSuchBeanDefinitionException e) {
				this.delegate.execute(command);
			}
		}
		this.delegate.execute(new TraceRunnable(this.traceManager, command));
	}

}
