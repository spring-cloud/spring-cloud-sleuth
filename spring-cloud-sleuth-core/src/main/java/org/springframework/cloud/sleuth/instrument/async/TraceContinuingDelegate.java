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

package org.springframework.cloud.sleuth.instrument.async;

import org.springframework.cloud.sleuth.SpanStarter;
import org.springframework.cloud.sleuth.Tracer;

/**
 * TraceDelegate that grants access to SpanStarter's utility methods
 *
 * @author Marcin Grzejszczak
 */
public abstract class TraceContinuingDelegate<T> extends TraceDelegate<T> {

	private final SpanStarter spanStarter;

	public TraceContinuingDelegate(Tracer tracer, T delegate) {
		this(tracer, delegate, null);
	}

	public TraceContinuingDelegate(Tracer tracer, T delegate, String name) {
		super(tracer, delegate, name);
		this.spanStarter = new SpanStarter(tracer);
	}

	public SpanStarter getSpanStarter() {
		return this.spanStarter;
	}
}
