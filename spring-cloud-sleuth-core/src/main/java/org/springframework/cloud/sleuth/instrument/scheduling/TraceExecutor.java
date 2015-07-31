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

import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.instrument.TraceRunnable;

/**
 * @author Dave Syer
 *
 */
@RequiredArgsConstructor
public class TraceExecutor implements Executor {

	private final Trace trace;
	private final Executor delegate;

	@Override
	public void execute(Runnable command) {
		this.delegate.execute(new TraceRunnable(this.trace, command));
	}

}
