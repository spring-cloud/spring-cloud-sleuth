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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.lang.NonNull;

/**
 * OpenTelemetry implementation of a {@link SpanCustomizer}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class OtelSpanCustomizer implements SpanCustomizer {

	private final Tracer tracer;

	private final Span span;

	public OtelSpanCustomizer(@NonNull Tracer tracer) {
		this.tracer = tracer;
		this.span = null;
	}

	public OtelSpanCustomizer(@NonNull Span span) {
		this.tracer = null;
		this.span = span;
	}

	@Override
	public SpanCustomizer name(String name) {
		currentSpan().updateName(name);
		return this;
	}

	private Span currentSpan() {
		return this.span != null ? this.span : Span.current();
	}

	@Override
	public SpanCustomizer tag(String key, String value) {
		currentSpan().setAttribute(key, value);
		return this;
	}

	@Override
	public SpanCustomizer event(String value) {
		currentSpan().addEvent(value);
		return this;
	}

}
