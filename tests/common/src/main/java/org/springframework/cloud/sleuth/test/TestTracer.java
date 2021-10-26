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

package org.springframework.cloud.sleuth.test;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.springframework.cloud.sleuth.BaggageInScope;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.ScopedSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanCustomizer;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.lang.Nullable;

public class TestTracer implements Tracer, AutoCloseable {

	private final Tracer delegate;

	final Queue<Span> createdSpans = new LinkedList<>();

	public TestTracer(Tracer delegate) {
		this.delegate = delegate;
	}

	@Override
	public Map<String, String> getAllBaggage() {
		return delegate.getAllBaggage();
	}

	@Override
	public BaggageInScope getBaggage(String name) {
		return delegate.getBaggage(name);
	}

	@Override
	public BaggageInScope getBaggage(TraceContext traceContext, String name) {
		return delegate.getBaggage(traceContext, name);
	}

	@Override
	public BaggageInScope createBaggage(String name) {
		return delegate.createBaggage(name);
	}

	@Override
	public BaggageInScope createBaggage(String name, String value) {
		return delegate.createBaggage(name, value);
	}

	@Override
	public Span nextSpan() {
		Span span = delegate.nextSpan();
		this.createdSpans.add(span);
		return span;
	}

	@Override
	public Span nextSpan(Span parent) {
		Span span = delegate.nextSpan(parent);
		this.createdSpans.add(span);
		return span;
	}

	@Override
	public SpanInScope withSpan(Span span) {
		return delegate.withSpan(span);
	}

	@Override
	public ScopedSpan startScopedSpan(String name) {
		return delegate.startScopedSpan(name);
	}

	@Override
	public Span.Builder spanBuilder() {
		return new TestSpanBuilder(delegate.spanBuilder(), this);
	}

	@Override
	public TraceContext.Builder traceContextBuilder() {
		return delegate.traceContextBuilder();
	}

	@Override
	@Nullable
	public SpanCustomizer currentSpanCustomizer() {
		return delegate.currentSpanCustomizer();
	}

	@Override
	@Nullable
	public Span currentSpan() {
		return delegate.currentSpan();
	}

	@Override
	public void close() throws Exception {
		this.createdSpans.clear();
	}

	public Queue<Span> createdSpans() {
		return createdSpans;
	}

	@Override
	public CurrentTraceContext currentTraceContext() {
		return this.delegate.currentTraceContext();
	}

}
