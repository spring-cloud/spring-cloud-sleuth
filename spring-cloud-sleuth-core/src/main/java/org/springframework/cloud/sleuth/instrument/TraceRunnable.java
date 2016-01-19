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

package org.springframework.cloud.sleuth.instrument;

import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.Tracer;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.cloud.sleuth.Tracer;

/**
 * @author Spencer Gibb
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class TraceRunnable extends TraceDelegate<Runnable> implements Runnable {

	public TraceRunnable(Tracer tracer, Runnable delegate) {
		super(tracer, delegate);
	}

	public TraceRunnable(Tracer tracer, Runnable delegate, String name) {
		super(tracer, delegate, name);
	}

	@Override
	public void run() {
		ensureThatThreadIsNotPollutedByPreviousTraces();
		Trace trace = startSpan();
		try {
			this.getDelegate().run();
		}
		finally {
			closeAll(trace);
		}
	}
}
