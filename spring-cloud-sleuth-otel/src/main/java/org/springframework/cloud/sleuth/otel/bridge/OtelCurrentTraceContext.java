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

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.TraceContext;

public class OtelCurrentTraceContext implements CurrentTraceContext {

	private static final Log log = LogFactory.getLog(OtelCurrentTraceContext.class);

	final Tracer tracer;

	public OtelCurrentTraceContext(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public TraceContext get() {
		Span currentSpan = this.tracer.getCurrentSpan();
		if (DefaultSpan.getInvalid().equals(currentSpan)) {
			return null;
		}
		return new OtelTraceContext(currentSpan);
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
		if (log.isDebugEnabled()) {
			log.debug("Will check if new scope should be created for context [" + context + "]");
		}
		if (context == null || SpanContext.getInvalid().equals(OtelTraceContext.toOtel(context))) {
			if (log.isDebugEnabled()) {
				log.debug("Invalid context - will return noop");
			}
			return OtelScope.NOOP;
		}
		Span fromContext = new SpanFromSpanContext(((OtelTraceContext) context).span,
				((OtelTraceContext) context).delegate);
		Span currentSpan = this.tracer.getCurrentSpan();
		if (log.isDebugEnabled()) {
			log.debug("Span from context [" + fromContext + "], current span [" + currentSpan + "]");
		}
		if (Objects.equals(fromContext, currentSpan)) {
			if (log.isDebugEnabled()) {
				log.debug("Same context as the current one - will return noop");
			}
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
