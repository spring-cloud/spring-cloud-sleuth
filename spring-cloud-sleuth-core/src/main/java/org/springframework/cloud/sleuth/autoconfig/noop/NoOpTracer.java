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

package org.springframework.cloud.sleuth.autoconfig.noop;

import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.sleuth.api.Baggage;
import org.springframework.cloud.sleuth.api.ScopedSpan;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.Tracer;

public class NoOpTracer implements Tracer {

	@Override
	public Span nextSpan(TraceContext parent) {
		return new NoOpSpan();
	}

	@Override
	public SpanInScope withSpan(Span span) {
		return new NoOpSpanInScope();
	}

	@Override
	public SpanCustomizer currentSpanCustomizer() {
		return new NoOpSpanCustomizer();
	}

	@Override
	public Span currentSpan() {
		return new NoOpSpan();
	}

	@Override
	public Span nextSpan() {
		return new NoOpSpan();
	}

	@Override
	public ScopedSpan startScopedSpan(String name) {
		return new NoOpScopedSpan();
	}

	@Override
	public ScopedSpan startScopedSpan(String name, Span parent) {
		return new NoOpScopedSpan();
	}

	@Override
	public Span.Builder spanBuilder() {
		return new NoOpSpanBuilder();
	}

	@Override
	public Map<String, String> getAllBaggage() {
		return new HashMap<>();
	}

	@Override
	public Baggage getBaggage(String name) {
		return new NoOpBaggage();
	}

	@Override
	public Baggage createBaggage(String name) {
		return new NoOpBaggage();
	}

}
