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

package org.springframework.cloud.sleuth.tracer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.BDDAssertions;

import org.springframework.cloud.sleuth.BaggageInScope;
import org.springframework.cloud.sleuth.ScopedSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanCustomizer;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;

/**
 * A noop implementation. Does nothing.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class SimpleTracer implements Tracer {

	public List<SimpleSpan> spans = new ArrayList<>();

	@Override
	public Span nextSpan(Span parent) {
		return new SimpleSpan();
	}

	public SimpleSpan getOnlySpan() {
		BDDAssertions.then(this.spans).hasSize(1);
		SimpleSpan span = this.spans.get(0);
		BDDAssertions.then(span.started).as("Span must be started").isTrue();
		BDDAssertions.then(span.ended).as("Span must be finished").isTrue();
		return span;
	}

	@Override
	public SpanInScope withSpan(Span span) {
		return new NoOpSpanInScope();
	}

	@Override
	public SpanCustomizer currentSpanCustomizer() {
		return null;
	}

	@Override
	public Span currentSpan() {
		return new SimpleSpan();
	}

	@Override
	public SimpleSpan nextSpan() {
		final SimpleSpan span = new SimpleSpan();
		this.spans.add(span);
		return span;
	}

	@Override
	public ScopedSpan startScopedSpan(String name) {
		return null;
	}

	@Override
	public Span.Builder spanBuilder() {
		return null;
	}

	@Override
	public TraceContext.Builder traceContextBuilder() {
		return null;
	}

	@Override
	public Map<String, String> getAllBaggage() {
		return new HashMap<>();
	}

	@Override
	public BaggageInScope getBaggage(String name) {
		return null;
	}

	@Override
	public BaggageInScope getBaggage(TraceContext traceContext, String name) {
		return null;
	}

	@Override
	public BaggageInScope createBaggage(String name) {
		return null;
	}

	@Override
	public BaggageInScope createBaggage(String name, String value) {
		return null;
	}

}
