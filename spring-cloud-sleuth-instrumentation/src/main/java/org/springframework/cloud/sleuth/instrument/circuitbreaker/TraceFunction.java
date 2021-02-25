/*
 * Copyright 2018-2021 the original author or authors.
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
import java.util.function.Function;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Trace representation of a {@link Function}.
 *
 * @param <T> type returned by the fallback
 * @since 2.2.1
 */
class TraceFunction<T> implements Function<Throwable, T> {

	private final Tracer tracer;

	private final Function<Throwable, T> delegate;

	private final AtomicReference<Span> span;

	TraceFunction(Tracer tracer, Function<Throwable, T> delegate) {
		this.tracer = tracer;
		this.delegate = delegate;
		this.span = new AtomicReference<>(this.tracer.nextSpan());
	}

	@Override
	public T apply(Throwable throwable) {
		String name = this.delegate.getClass().getSimpleName();
		Span span = this.span.get().name(name);
		Throwable tr = null;
		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			return this.delegate.apply(throwable);
		}
		catch (Throwable t) {
			tr = t;
			throw t;
		}
		finally {
			if (tr != null) {
				span.error(tr);
			}
			span.end();
			this.span.set(null);
		}
	}

}
