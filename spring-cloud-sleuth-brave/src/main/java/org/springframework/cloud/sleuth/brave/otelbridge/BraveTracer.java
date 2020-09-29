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

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

import org.springframework.util.Assert;

/**
 * Brave version of {@link Tracer}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class BraveTracer implements Tracer {

	private final brave.Tracer tracer;

	public BraveTracer(brave.Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public Span getCurrentSpan() {
		brave.Span span = tracer.currentSpan();
		if (span == null) {
			return DefaultSpan.getInvalid();
		}
		return new BraveSpan(span);
	}

	@Override
	public Scope withSpan(Span span) {
		Assert.notNull(span, "Span must not be null");
		brave.Span braveSpan = span == DefaultSpan.getInvalid() ? null : ((BraveSpan) span).span;
		return new BraveScope(tracer.withSpanInScope(braveSpan));
	}

	@Override
	public Span.Builder spanBuilder(String spanName) {
		return new BraveSpanBuilder(this.tracer, spanName);
	}

	/**
	 * @return Brave's {@link brave.Tracer}.
	 */
	public brave.Tracer tracer() {
		return this.tracer;
	}

}
