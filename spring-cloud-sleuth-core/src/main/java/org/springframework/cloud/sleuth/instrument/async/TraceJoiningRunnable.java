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

import org.springframework.cloud.sleuth.SpanHolder;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.Tracer;

/**
 * @author Spencer Gibb
 */
public class TraceJoiningRunnable extends TraceDelegate<Runnable> implements Runnable {

	public TraceJoiningRunnable(Tracer tracer, Runnable delegate) {
		super(tracer, delegate);
	}

	public TraceJoiningRunnable(Tracer tracer, Runnable delegate, SpanName name) {
		super(tracer, delegate, name);
	}

	@Override
	public void run() {
		SpanHolder.span(getTracer()).startOrContinueSpan(getSpanName(), getParent());
		this.getDelegate().run();
	}
}
