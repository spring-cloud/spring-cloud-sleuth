/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.scheduling;

import java.util.concurrent.Executor;

import lombok.RequiredArgsConstructor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;

/**
 * @author Dave Syer
 *
 */
@RequiredArgsConstructor
public class LazyTraceAsyncCustomizer extends AsyncConfigurerSupport {

	private Trace trace;
	private final BeanFactory beanFactory;
	private final AsyncConfigurer delegate;

	@Override
	public Executor getAsyncExecutor() {
		if (this.trace == null) {
			this.trace = this.beanFactory.getBean(Trace.class);
		}
		return new TraceExecutor(this.trace, this.delegate.getAsyncExecutor());
	}

	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return this.delegate.getAsyncUncaughtExceptionHandler();
	}

}
