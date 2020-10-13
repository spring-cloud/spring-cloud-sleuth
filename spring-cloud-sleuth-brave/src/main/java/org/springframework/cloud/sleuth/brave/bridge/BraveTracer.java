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

package org.springframework.cloud.sleuth.brave.bridge;

import brave.propagation.TraceContextOrSamplingFlags;

import org.springframework.cloud.sleuth.api.SamplerFunction;
import org.springframework.cloud.sleuth.api.SamplingFlags;
import org.springframework.cloud.sleuth.api.ScopedSpan;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.Tracer;

public class BraveTracer implements Tracer {

	private final brave.Tracer tracer;

	public BraveTracer(brave.Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public Span newTrace() {
		return new BraveSpan(this.tracer.newTrace());
	}

	@Override
	public Span joinSpan(TraceContext context) {
		return new BraveSpan(this.tracer.joinSpan(((BraveTraceContext) context).traceContext));
	}

	@Override
	public Span newChild(TraceContext parent) {
		return new BraveSpan(this.tracer.newChild(((BraveTraceContext) parent).traceContext));
	}

	@Override
	public Span nextSpan(TraceContext extracted) {
		brave.propagation.TraceContext context = extracted != null ? (((BraveTraceContext) extracted).traceContext)
				: null;
		if (context == null) {
			return null;
		}
		return new BraveSpan(this.tracer.nextSpan(TraceContextOrSamplingFlags.create(context)));
	}

	@Override
	public Span nextSpan(SamplingFlags extracted) {
		if (extracted instanceof BraveTraceContext) {
			return new BraveSpan(this.tracer
					.nextSpan(TraceContextOrSamplingFlags.create(((BraveTraceContext) extracted).traceContext)));
		}
		return new BraveSpan(this.tracer
				.nextSpan(TraceContextOrSamplingFlags.create(((BraveSamplingFlags) extracted).samplingFlags)));
	}

	@Override
	public Span toSpan(TraceContext context) {
		return new BraveSpan(this.tracer.toSpan(((BraveTraceContext) context).traceContext));
	}

	@Override
	public SpanInScope withSpanInScope(Span span) {
		return new BraveSpanInScope(tracer.withSpanInScope(span == null ? null : ((BraveSpan) span).delegate));
	}

	@Override
	public SpanCustomizer currentSpanCustomizer() {
		return new BraveSpanCustomizer(this.tracer.currentSpanCustomizer());
	}

	@Override
	public Span currentSpan() {
		brave.Span currentSpan = this.tracer.currentSpan();
		if (currentSpan == null) {
			return null;
		}
		return new BraveSpan(currentSpan);
	}

	@Override
	public Span nextSpan() {
		return new BraveSpan(this.tracer.nextSpan());
	}

	@Override
	public ScopedSpan startScopedSpan(String name) {
		return new BraveScopedSpan(this.tracer.startScopedSpan(name));
	}

	@Override
	public <T> ScopedSpan startScopedSpan(String name, SamplerFunction<T> samplerFunction, T arg) {
		return new BraveScopedSpan(
				this.tracer.startScopedSpan(name, ((BraveSamplerFunction) samplerFunction).samplerFunction, arg));
	}

	@Override
	public <T> Span nextSpan(SamplerFunction<T> samplerFunction, T arg) {
		return new BraveSpan(this.tracer.nextSpan(((BraveSamplerFunction) samplerFunction).samplerFunction, arg));
	}

	@Override
	public <T> Span nextSpanWithParent(SamplerFunction<T> samplerFunction, T arg, TraceContext parent) {
		brave.propagation.TraceContext context = parent != null ? (((BraveTraceContext) parent).traceContext) : null;
		return new BraveSpan(
				this.tracer.nextSpanWithParent(((BraveSamplerFunction) samplerFunction).samplerFunction, arg, context));
	}

	@Override
	public ScopedSpan startScopedSpanWithParent(String name, Span parent) {
		brave.propagation.TraceContext context = parent == null ? null : BraveTraceContext.toBrave(parent.context());
		return new BraveScopedSpan(this.tracer.startScopedSpanWithParent(name, context));
	}

	@Override
	public Span.Builder spanBuilder() {
		return new BraveSpanBuilder(this.tracer);
	}

	public static Tracer fromBrave(brave.Tracer tracer) {
		return new BraveTracer(tracer);
	}

}

class BraveSpanInScope implements Tracer.SpanInScope {

	final brave.Tracer.SpanInScope delegate;

	BraveSpanInScope(brave.Tracer.SpanInScope delegate) {
		this.delegate = delegate;
	}

	@Override
	public void close() {
		this.delegate.close();
	}

}