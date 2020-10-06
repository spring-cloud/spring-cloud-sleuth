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

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.SpanContext;

import org.springframework.cloud.sleuth.api.SamplerFunction;
import org.springframework.cloud.sleuth.api.SamplingFlags;
import org.springframework.cloud.sleuth.api.ScopedSpan;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.Tracer;

public class OtelTracer implements Tracer {

	private final io.opentelemetry.trace.Tracer tracer;

	public OtelTracer(io.opentelemetry.trace.Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public Span newTrace() {
		return new OtelSpan(this.tracer.spanBuilder("").setNoParent().startSpan());
	}

	@Override
	public Span joinSpan(TraceContext context) {
		// TODO: [OTEL] I think you can't join a span in Otel
		return newChild(context);
	}

	@Override
	public Span newChild(TraceContext parent) {
		return new OtelSpan(this.tracer.spanBuilder("").setParent(((OtelTraceContext) parent).delegate).startSpan());
	}

	@Override
	public Span nextSpan(TraceContext extracted) {
		SpanContext context = extracted != null ? (((OtelTraceContext) extracted).delegate) : null;
		if (context == null) {
			return null;
		}
		return new OtelSpan(this.tracer.spanBuilder("").setParent(((OtelTraceContext) extracted).delegate).startSpan());
	}

	@Override
	public Span nextSpan(SamplingFlags extracted) {
		// TODO: [OTEL] will create a child span
		if (extracted instanceof OtelTraceContext) {
			return new OtelSpan(
					this.tracer.spanBuilder("").setParent(((OtelTraceContext) extracted).delegate).startSpan());
		}
		io.opentelemetry.trace.Span startedSpan = this.tracer.spanBuilder("").startSpan();
		OtelTraceContext context = (OtelTraceContext) new OtelTraceContextBuilder(startedSpan.getContext())
				.sampled(extracted.sampled()).build();
		return new OtelSpan(this.tracer.spanBuilder("").setParent(context.delegate).startSpan());
	}

	@Override
	public Span toSpan(TraceContext context) {
		// TODO: [OTEL] Not advised to be used by Brave
		return null;
	}

	@Override
	public SpanInScope withSpanInScope(Span span) {
		return new OtelSpanInScope(
				tracer.withSpan(span == null ? DefaultSpan.getInvalid() : ((OtelSpan) span).delegate));
	}

	@Override
	public SpanCustomizer currentSpanCustomizer() {
		return new OtelSpanCustomizer(this.tracer);
	}

	@Override
	public Span currentSpan() {
		io.opentelemetry.trace.Span currentSpan = this.tracer.getCurrentSpan();
		if (currentSpan == null) {
			return null;
		}
		return new OtelSpan(currentSpan);
	}

	@Override
	public Span nextSpan() {
		return new OtelSpan(this.tracer.spanBuilder("").startSpan());
	}

	@Override
	public ScopedSpan startScopedSpan(String name) {
		io.opentelemetry.trace.Span span = this.tracer.spanBuilder(name).startSpan();
		return new OtelScopedSpan(span, this.tracer.withSpan(span));
	}

	@Override
	public <T> ScopedSpan startScopedSpan(String name, SamplerFunction<T> samplerFunction, T arg) {
		// TODO: [OTEL] No sampling on the level of the API
		return null;
	}

	@Override
	public <T> Span nextSpan(SamplerFunction<T> samplerFunction, T arg) {
		// TODO: [OTEL] No sampling on the level of the API
		return null;
	}

	@Override
	public <T> Span nextSpanWithParent(SamplerFunction<T> samplerFunction, T arg, TraceContext parent) {
		// TODO: [OTEL] No sampling on the level of the API
		return null;
	}

	@Override
	public ScopedSpan startScopedSpanWithParent(String name, TraceContext parent) {
		SpanContext context = parent != null ? (((OtelTraceContext) parent).delegate) : null;
		io.opentelemetry.trace.Span span = this.tracer.spanBuilder(name).setParent(context).startSpan();
		return new OtelScopedSpan(span, this.tracer.withSpan(span));
	}

	public static Tracer fromOtel(io.opentelemetry.trace.Tracer tracer) {
		return new OtelTracer(tracer);
	}

}

class OtelSpanInScope implements Tracer.SpanInScope {

	final Scope delegate;

	OtelSpanInScope(Scope delegate) {
		this.delegate = delegate;
	}

	@Override
	public void close() {
		this.delegate.close();
	}

}