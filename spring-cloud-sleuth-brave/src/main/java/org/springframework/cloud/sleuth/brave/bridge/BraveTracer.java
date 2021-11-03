/*
 * Copyright 2013-2021 the original author or authors.
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

import java.util.Map;

import brave.propagation.TraceContextOrSamplingFlags;

import org.springframework.cloud.sleuth.BaggageInScope;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.ScopedSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanCustomizer;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;

/**
 * Brave implementation of a {@link Tracer}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class BraveTracer implements Tracer {

	private final brave.Tracer tracer;

	private final BraveBaggageManager braveBaggageManager;

	private final CurrentTraceContext currentTraceContext;

	@Deprecated
	public BraveTracer(brave.Tracer tracer, BraveBaggageManager braveBaggageManager) {
		this.tracer = tracer;
		this.braveBaggageManager = braveBaggageManager;
		this.currentTraceContext = null;
	}

	public BraveTracer(brave.Tracer tracer, CurrentTraceContext context, BraveBaggageManager braveBaggageManager) {
		this.tracer = tracer;
		this.braveBaggageManager = braveBaggageManager;
		this.currentTraceContext = context;
	}

	@Override
	public Span nextSpan(Span parent) {
		if (parent == null) {
			return nextSpan();
		}
		brave.propagation.TraceContext context = (((BraveTraceContext) parent.context()).traceContext);
		if (context == null) {
			return null;
		}
		return new BraveSpan(this.tracer.nextSpan(TraceContextOrSamplingFlags.create(context)));
	}

	@Override
	public SpanInScope withSpan(Span span) {
		return new BraveSpanInScope(
				tracer.withSpanInScope(span == null ? null : ((BraveSpan) AssertingSpan.unwrap(span)).delegate));
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
	public Span.Builder spanBuilder() {
		return new BraveSpanBuilder(this.tracer);
	}

	@Override
	public TraceContext.Builder traceContextBuilder() {
		return new BraveTraceContextBuilder();
	}

	@Override
	public Map<String, String> getAllBaggage() {
		return this.braveBaggageManager.getAllBaggage();
	}

	@Override
	public BaggageInScope getBaggage(String name) {
		return this.braveBaggageManager.getBaggage(name);
	}

	@Override
	public BaggageInScope getBaggage(TraceContext traceContext, String name) {
		return this.braveBaggageManager.getBaggage(traceContext, name);
	}

	@Override
	public BaggageInScope createBaggage(String name) {
		return this.braveBaggageManager.createBaggage(name);
	}

	@Override
	public BaggageInScope createBaggage(String name, String value) {
		return this.braveBaggageManager.createBaggage(name).set(value);
	}

	@Override
	public CurrentTraceContext currentTraceContext() {
		return this.currentTraceContext;
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
