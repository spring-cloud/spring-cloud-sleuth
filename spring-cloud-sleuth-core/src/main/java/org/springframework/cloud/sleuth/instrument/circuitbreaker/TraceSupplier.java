/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.circuitbreaker;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import brave.Span;
import brave.Tracer;

public class TraceSupplier<T> implements Supplier<T> {

	private final Tracer tracer;

	private final Supplier<T> delegate;

	private final AtomicReference<Span> span;

	public TraceSupplier(Tracer tracer, Supplier<T> delegate) {
		this.tracer = tracer;
		this.delegate = delegate;
		this.span = new AtomicReference<>(this.tracer.nextSpan());
	}

	@Override
	public T get() {
		String name = this.delegate.getClass().getSimpleName();
		Span span = this.span.get().name(name);
		Throwable tr = null;
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			return this.delegate.get();
		}
		catch (Throwable t) {
			tr = t;
			throw t;
		}
		finally {
			if (tr != null) {
				String message = tr.getMessage() == null ? tr.getClass().getSimpleName()
						: tr.getMessage();
				span.tag("error", message);
			}
			span.finish();
			this.span.set(null);
		}
	}

}
