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

package org.springframework.cloud.sleuth.brave.otelbridge;

import brave.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;

public class BraveTracer implements io.opentelemetry.trace.Tracer {

	private final brave.Tracer tracer;

	public BraveTracer(brave.Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public Span getCurrentSpan() {
		return new BraveSpan(tracer.currentSpan());
	}

	@Override
	public Scope withSpan(Span span) {
		return new BraveScope(tracer.withSpanInScope(((BraveSpan) span).span));
	}

	@Override
	public Span.Builder spanBuilder(String spanName) {
		return new BraveSpanBuilder(this.tracer, spanName);
	}

	public brave.Tracer tracer() {
		return this.tracer;
	}

}
