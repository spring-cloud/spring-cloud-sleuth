/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.reactive;

import java.util.concurrent.TimeUnit;

import reactor.core.Cancellation;
import reactor.core.scheduler.TimedScheduler;

/**
 * @author Marcin Grzejszczak
 */
final class TraceTimedScheduler implements TimedScheduler {

	final TimedScheduler timedScheduler;

	public TraceTimedScheduler(TimedScheduler timedScheduler) {
		this.timedScheduler = timedScheduler;
	}

	@Override public Cancellation schedule(Runnable task, long delay, TimeUnit unit) {
		return timedScheduler.schedule(task, delay, unit);
	}

	@Override public Cancellation schedulePeriodically(Runnable task, long initialDelay,
			long period, TimeUnit unit) {
		return timedScheduler.schedulePeriodically(task, initialDelay, period, unit);
	}

	@Override public long now(TimeUnit unit) {
		return timedScheduler.now(unit);
	}

	@Override public TimedWorker createWorker() {
		return timedScheduler.createWorker();
	}

	@Override public Cancellation schedule(Runnable task) {
		return timedScheduler.schedule(task);
	}

	@Override public void start() {
		timedScheduler.start();
	}

	@Override public void shutdown() {
		timedScheduler.shutdown();
	}
}
