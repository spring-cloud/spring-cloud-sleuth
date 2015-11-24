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

package org.springframework.cloud.sleuth.instrument.concurrent.executor;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.instrument.concurrent.executor.support.TraceCallableSupport;
import org.springframework.cloud.sleuth.instrument.concurrent.executor.support.TraceRunnableSupport;

/**
 * A decorator class for {@link ScheduledExecutorService} to support tracing in Executors
 * @author Gaurav Rai Mazra
 *
 */
public class TraceableScheduledExecutorService extends TracableExecutorService implements ScheduledExecutorService {
	
	public TraceableScheduledExecutorService(final ScheduledExecutorService delegate, final Trace trace) {
		super(delegate, trace);
	}
	
	private ScheduledExecutorService getScheduledExecutorService() {
		return (ScheduledExecutorService) this.delegate;
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		Runnable r = TraceRunnableSupport.decorate(this.trace, command);
		return getScheduledExecutorService().schedule(r, delay, unit);
	}
	
	

	@Override
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
		Callable<V> c = TraceCallableSupport.decorate(this.trace, callable);
		return getScheduledExecutorService().schedule(c, delay, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
		Runnable r = TraceRunnableSupport.decorate(this.trace, command);
		return getScheduledExecutorService().scheduleAtFixedRate(r, initialDelay, period, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
		Runnable r = TraceRunnableSupport.decorate(this.trace, command);
		return getScheduledExecutorService().scheduleWithFixedDelay(r, initialDelay, delay, unit);
	}

}
