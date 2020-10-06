/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.otel.bridge;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.TraceContext;

public class OtelCurrentTraceContext implements CurrentTraceContext {

	final Tracer tracer;

	public OtelCurrentTraceContext(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public TraceContext get() {
		return new OtelTraceContext(this.tracer.getCurrentSpan());
	}

	@Override
	public Scope newScope(TraceContext context) {
		// TODO: [OTEL] Maybe that's not necessary
		Span fromContext = new SpanFromSpanContext(((OtelTraceContext) context).span,
				((OtelTraceContext) context).delegate);
		return new OtelScope(new OtelSpanInScope(this.tracer.withSpan(fromContext)));
	}

	@Override
	public Scope maybeScope(TraceContext context) {
		Span fromContext = new SpanFromSpanContext(((OtelTraceContext) context).span,
				((OtelTraceContext) context).delegate);
		Span currentSpan = this.tracer.getCurrentSpan();
		if (fromContext.equals(currentSpan)) {
			return OtelScope.NOOP;
		}
		return newScope(context);
	}

	@Override
	public <C> Callable<C> wrap(Callable<C> task) {
		// TODO: [OTEL] Not related. Shouldn't be here
		return null;
	}

	@Override
	public Runnable wrap(Runnable task) {
		// TODO: [OTEL] Not related. Shouldn't be here
		return null;
	}

	@Override
	public Executor executor(Executor delegate) {
		// TODO: [OTEL] Not related. Shouldn't be here
		return null;
	}

	@Override
	public ExecutorService executorService(ExecutorService delegate) {
		// TODO: [OTEL] Not related. Shouldn't be here
		return null;
	}

}

class OtelScope implements CurrentTraceContext.Scope {

	private final OtelSpanInScope delegate;

	OtelScope(OtelSpanInScope delegate) {
		this.delegate = delegate;
	}

	@Override
	public void close() {
		this.delegate.close();
	}

	static CurrentTraceContext.Scope NOOP = new CurrentTraceContext.Scope() {
		@Override
		public void close() {
		}

		@Override
		public String toString() {
			return "NoopScope";
		}
	};

}
