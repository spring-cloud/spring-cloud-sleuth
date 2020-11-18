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
import java.util.concurrent.atomic.AtomicReference;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.lang.Nullable;

/**
 * OpenTelemetry implementation of a {@link Span}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class OtelSpan implements Span {

	final io.opentelemetry.api.trace.Span delegate;

	private final AtomicReference<Context> context;

	OtelSpan(io.opentelemetry.api.trace.Span delegate) {
		this.delegate = delegate;
		if (delegate instanceof SpanFromSpanContext) {
			SpanFromSpanContext fromSpanContext = (SpanFromSpanContext) delegate;
			this.context = fromSpanContext.otelTraceContext.context;
		}
		else {
			this.context = new AtomicReference<>(Context.current());
		}
	}

	OtelSpan(io.opentelemetry.api.trace.Span delegate, Context context) {
		this.delegate = delegate;
		this.context = new AtomicReference<>(context);
	}

	static io.opentelemetry.api.trace.Span toOtel(Span span) {
		return ((OtelSpan) span).delegate;
	}

	static Span fromOtel(io.opentelemetry.api.trace.Span span) {
		return new OtelSpan(span);
	}

	static Span fromOtel(io.opentelemetry.api.trace.Span span, Context context) {
		return new OtelSpan(span, context);
	}

	@Override
	public boolean isNoop() {
		return !this.delegate.isRecording();
	}

	@Override
	public TraceContext context() {
		if (this.delegate == null) {
			return null;
		}
		return new OtelTraceContext(this.context, this.delegate.getSpanContext(), this.delegate);
	}

	@Override
	public Span start() {
		// they are already started via the builder
		return this;
	}

	@Override
	public Span name(String name) {
		this.delegate.updateName(name);
		return new OtelSpan(this.delegate);
	}

	@Override
	public Span event(String value) {
		this.delegate.addEvent(value);
		return new OtelSpan(this.delegate);
	}

	@Override
	public Span tag(String key, String value) {
		this.delegate.setAttribute(key, value);
		return new OtelSpan(this.delegate);
	}

	@Override
	public Span error(Throwable throwable) {
		this.delegate.recordException(throwable);
		return new OtelSpan(this.delegate);
	}

	@Override
	public void end() {
		this.delegate.end();
	}

	@Override
	public void abandon() {
		// TODO: [OTEL] doesn't seem to have this notion yet
	}

	@Override
	public String toString() {
		return this.delegate != null ? this.delegate.toString() : "null";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		OtelSpan otelSpan = (OtelSpan) o;
		return Objects.equals(this.delegate, otelSpan.delegate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.delegate);
	}

}

class SpanFromSpanContext implements io.opentelemetry.api.trace.Span {

	final io.opentelemetry.api.trace.Span span;

	final SpanContext newSpanContext;

	final OtelTraceContext otelTraceContext;

	SpanFromSpanContext(io.opentelemetry.api.trace.Span span, SpanContext newSpanContext,
			OtelTraceContext otelTraceContext) {
		this.span = span;
		this.newSpanContext = newSpanContext;
		this.otelTraceContext = otelTraceContext;
	}

	@Override
	public io.opentelemetry.api.trace.Span setAttribute(String key, @Nullable String value) {
		return span.setAttribute(key, value);
	}

	@Override
	public io.opentelemetry.api.trace.Span setAttribute(String key, long value) {
		return span.setAttribute(key, value);
	}

	@Override
	public io.opentelemetry.api.trace.Span setAttribute(String key, double value) {
		return span.setAttribute(key, value);
	}

	@Override
	public io.opentelemetry.api.trace.Span setAttribute(String key, boolean value) {
		return span.setAttribute(key, value);
	}

	@Override
	public io.opentelemetry.api.trace.Span addEvent(String name) {
		return span.addEvent(name);
	}

	@Override
	public io.opentelemetry.api.trace.Span addEvent(String name, long timestamp) {
		return span.addEvent(name, timestamp);
	}

	@Override
	public io.opentelemetry.api.trace.Span addEvent(String name, Attributes attributes) {
		return span.addEvent(name, attributes);
	}

	@Override
	public io.opentelemetry.api.trace.Span addEvent(String name, Attributes attributes, long timestamp) {
		return span.addEvent(name, attributes, timestamp);
	}

	@Override
	public io.opentelemetry.api.trace.Span setStatus(StatusCode canonicalCode) {
		return span.setStatus(canonicalCode);
	}

	@Override
	public io.opentelemetry.api.trace.Span setStatus(StatusCode canonicalCode, String description) {
		return span.setStatus(canonicalCode, description);
	}

	@Override
	public io.opentelemetry.api.trace.Span setAttribute(AttributeKey<Long> key, int value) {
		return span.setAttribute(key, value);
	}

	@Override
	public <T> io.opentelemetry.api.trace.Span setAttribute(AttributeKey<T> key, T value) {
		return span.setAttribute(key, value);
	}

	@Override
	public io.opentelemetry.api.trace.Span recordException(Throwable exception) {
		return span.recordException(exception);
	}

	@Override
	public io.opentelemetry.api.trace.Span recordException(Throwable exception, Attributes additionalAttributes) {
		return span.recordException(exception, additionalAttributes);
	}

	@Override
	public io.opentelemetry.api.trace.Span updateName(String name) {
		return span.updateName(name);
	}

	@Override
	public void end() {
		span.end();
	}

	@Override
	public void end(long l) {
		span.end(l);
	}

	@Override
	public SpanContext getSpanContext() {
		return newSpanContext;
	}

	@Override
	public boolean isRecording() {
		return span.isRecording();
	}

	@Override
	public String toString() {
		return "SpanFromSpanContext{" + "span=" + span + ", newSpanContext=" + newSpanContext + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SpanFromSpanContext that = (SpanFromSpanContext) o;
		return Objects.equals(span, that.span) && Objects.equals(this.newSpanContext, that.newSpanContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.span, this.newSpanContext);
	}

}
