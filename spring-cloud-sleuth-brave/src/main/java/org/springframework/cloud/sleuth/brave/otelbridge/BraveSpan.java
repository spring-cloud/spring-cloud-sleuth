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

import javax.annotation.Nullable;

import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.trace.EndSpanOptions;
import io.opentelemetry.trace.Event;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Status;

public class BraveSpan implements io.opentelemetry.trace.Span {

	final brave.Span span;

	String name;

	public BraveSpan(brave.Span span) {
		this.span = span;
	}

	public BraveSpan(brave.Span span, String name) {
		this.span = span;
		this.name = name;
	}

	@Override
	public void setAttribute(String key, @Nullable String value) {
		this.span.tag(key, value);
	}

	@Override
	public void setAttribute(String key, long value) {
		setAttribute(key, String.valueOf(value));
	}

	@Override
	public void setAttribute(String key, double value) {
		setAttribute(key, String.valueOf(value));
	}

	@Override
	public void setAttribute(String key, boolean value) {
		setAttribute(key, String.valueOf(value));
	}

	@Override
	public void setAttribute(String key, AttributeValue value) {
		setAttribute(key, String.valueOf(value));
	}

	@Override
	public void addEvent(String name) {
		this.span.annotate(name);
	}

	@Override
	public void addEvent(String name, long timestamp) {
		this.span.annotate(timestamp, name);
	}

	@Override
	public void addEvent(String name, Attributes attributes) {
		// TODO: What to do about attributes
	}

	@Override
	public void addEvent(String name, Attributes attributes, long timestamp) {
		// TODO: What to do about attributes
	}

	@Override
	public void addEvent(Event event) {
		addEvent(event.getName());
	}

	@Override
	public void addEvent(Event event, long timestamp) {
		addEvent(event.getName(), timestamp);
	}

	@Override
	public void setStatus(Status status) {
		// TODO: What to do about status
	}

	@Override
	public void recordException(Throwable exception) {
		this.span.error(exception);
	}

	@Override
	public void recordException(Throwable exception, Attributes additionalAttributes) {
		// TODO: What about attributes
		this.span.error(exception);
	}

	@Override
	public void updateName(String name) {
		this.span.name(name);
		this.name = name;
	}

	@Override
	public void end() {
		this.span.finish();
	}

	@Override
	public void end(EndSpanOptions endOptions) {
		this.span.finish(endOptions.getEndTimestamp());
	}

	@Override
	public SpanContext getContext() {
		return new BraveSpanContext(this.span.context());
	}

	@Override
	public boolean isRecording() {
		return !this.span.isNoop();
	}

}
