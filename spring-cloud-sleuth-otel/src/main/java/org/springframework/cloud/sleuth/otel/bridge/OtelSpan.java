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

import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.trace.EndSpanOptions;
import io.opentelemetry.trace.Event;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Status;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.lang.Nullable;

public class OtelSpan implements Span {

	final io.opentelemetry.trace.Span delegate;

	public OtelSpan(io.opentelemetry.trace.Span delegate) {
		this.delegate = delegate;
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
		return new OtelTraceContext(this.delegate);
	}

	@Override
	public SpanCustomizer customizer() {
		return new OtelSpanCustomizer(this.delegate);
	}

	@Override
	public Span start() {
		// TODO: [OTEL] they are already started via the builder
		return this;
	}

	@Override
	public Span start(long timestamp) {
		// TODO: Otel we'd need to go via builder
		return null;
	}

	@Override
	public Span name(String name) {
		this.delegate.updateName(name);
		return new OtelSpan(this.delegate);
	}

	@Override
	public Span kind(Kind kind) {
		// TODO: Otel we'd need to go via builder
		return null;
	}

	@Override
	public Span annotate(String value) {
		this.delegate.addEvent(value);
		return new OtelSpan(this.delegate);
	}

	@Override
	public Span annotate(long timestamp, String value) {
		this.delegate.addEvent(value, timestamp);
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
	public Span remoteServiceName(String remoteServiceName) {
		// TODO: [OTEL] doesn't seem to have this notion
		return null;
	}

	@Override
	public boolean remoteIpAndPort(String remoteIp, int remotePort) {
		// TODO: [OTEL] doesn't seem to have this notion
		return false;
	}

	@Override
	public void finish() {
		this.delegate.end();
	}

	@Override
	public void abandon() {
		// TODO: [OTEL] doesn't seem to have this notion
	}

	@Override
	public void finish(long timestamp) {
		this.delegate.end(EndSpanOptions.builder().setEndTimestamp(timestamp).build());
	}

	@Override
	public void flush() {
		// TODO: [OTEL] doesn't seem to have this notion
	}

	@Override
	public String toString() {
		return this.delegate != null ? this.delegate.toString() : "null";
	}

	public static io.opentelemetry.trace.Span toOtel(Span span) {
		return ((OtelSpan) span).delegate;
	}

	public static Span fromOtel(io.opentelemetry.trace.Span span) {
		return new OtelSpan(span);
	}

}

class SpanFromSpanContext implements io.opentelemetry.trace.Span {

	final io.opentelemetry.trace.Span span;

	final SpanContext newSpanContext;

	SpanFromSpanContext(io.opentelemetry.trace.Span span, SpanContext newSpanContext) {
		this.span = span;
		this.newSpanContext = newSpanContext;
	}

	@Override
	public void setAttribute(String key, @Nullable String value) {
		span.setAttribute(key, value);
	}

	@Override
	public void setAttribute(String key, long value) {
		span.setAttribute(key, value);
	}

	@Override
	public void setAttribute(String key, double value) {
		span.setAttribute(key, value);
	}

	@Override
	public void setAttribute(String key, boolean value) {
		span.setAttribute(key, value);
	}

	@Override
	public void setAttribute(String key, AttributeValue value) {
		span.setAttribute(key, value);
	}

	@Override
	public void addEvent(String name) {
		span.addEvent(name);
	}

	@Override
	public void addEvent(String name, long timestamp) {
		span.addEvent(name, timestamp);
	}

	@Override
	public void addEvent(String name, Attributes attributes) {
		span.addEvent(name, attributes);
	}

	@Override
	public void addEvent(String name, Attributes attributes, long timestamp) {
		span.addEvent(name, attributes, timestamp);
	}

	@Override
	public void addEvent(Event event) {
		span.addEvent(event);
	}

	@Override
	public void addEvent(Event event, long timestamp) {
		span.addEvent(event, timestamp);
	}

	@Override
	public void setStatus(Status status) {
		span.setStatus(status);
	}

	@Override
	public void recordException(Throwable exception) {
		span.recordException(exception);
	}

	@Override
	public void recordException(Throwable exception, Attributes additionalAttributes) {
		span.recordException(exception, additionalAttributes);
	}

	@Override
	public void updateName(String name) {
		span.updateName(name);
	}

	@Override
	public void end() {
		span.end();
	}

	@Override
	public void end(EndSpanOptions endOptions) {
		span.end(endOptions);
	}

	@Override
	public SpanContext getContext() {
		return newSpanContext;
	}

	@Override
	public boolean isRecording() {
		return span.isRecording();
	}

}