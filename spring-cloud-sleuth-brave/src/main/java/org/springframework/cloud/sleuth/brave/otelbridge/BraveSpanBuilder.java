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

import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import brave.Tracer;
import io.grpc.Context;
import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.trace.Link;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;

/**
 * Brave version of {@link Span.Builder}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class BraveSpanBuilder implements Span.Builder {

	private final Tracer tracer;

	private final String name;

	private BraveSpanContext parent;

	private final List<AbstractMap.SimpleEntry<String, String>> tags = new LinkedList<>();

	private brave.Span.Kind kind;

	private Long startTimestamp;

	public BraveSpanBuilder(Tracer tracer) {
		this(tracer, null);
	}

	public BraveSpanBuilder(Tracer tracer, String name) {
		this.tracer = tracer;
		this.name = name;
	}

	@Override
	public Span.Builder setParent(Span parent) {
		if (parent == null) {
			return this;
		}
		this.parent = (BraveSpanContext) parent.getContext();
		return this;
	}

	@Override
	public Span.Builder setParent(SpanContext remoteParent) {
		if (remoteParent == null) {
			return this;
		}
		this.parent = (BraveSpanContext) remoteParent;
		return this;
	}

	@Override
	public Span.Builder setParent(Context context) {
		// TODO: ...
		return this;
	}

	@Override
	public Span.Builder setNoParent() {
		this.parent = null;
		return this;
	}

	@Override
	public Span.Builder addLink(SpanContext spanContext) {
		// TODO: How to support links?
		return this;
	}

	@Override
	public Span.Builder addLink(SpanContext spanContext, Attributes attributes) {
		// TODO: How to support links?
		return this;
	}

	@Override
	public Span.Builder addLink(Link link) {
		// TODO: How to support links?
		return this;
	}

	@Override
	public Span.Builder setAttribute(String key, @Nullable String value) {
		tags.add(new AbstractMap.SimpleEntry<>(key, value));
		return this;
	}

	@Override
	public Span.Builder setAttribute(String key, long value) {
		return setAttribute(key, String.valueOf(value));
	}

	@Override
	public Span.Builder setAttribute(String key, double value) {
		return setAttribute(key, String.valueOf(value));
	}

	@Override
	public Span.Builder setAttribute(String key, boolean value) {
		return setAttribute(key, String.valueOf(value));
	}

	@Override
	public Span.Builder setAttribute(String key, AttributeValue value) {
		return setAttribute(key, value.getStringValue());
	}

	@Override
	public Span.Builder setSpanKind(Span.Kind spanKind) {
		switch (spanKind) {
		case INTERNAL:
			break;
		case SERVER:
			kind = brave.Span.Kind.SERVER;
			break;
		case CLIENT:
			kind = brave.Span.Kind.CLIENT;
			break;
		case PRODUCER:
			kind = brave.Span.Kind.PRODUCER;
			break;
		case CONSUMER:
			kind = brave.Span.Kind.CONSUMER;
			break;
		}
		return this;
	}

	@Override
	public Span.Builder setStartTimestamp(long startTimestamp) {
		this.startTimestamp = startTimestamp;
		return this;
	}

	@Override
	public Span startSpan() {
		brave.Span nextSpan = nextSpan();
		if (this.name != null) {
			nextSpan.name(this.name);
		}
		nextSpan.kind(this.kind);
		this.tags.forEach(e -> nextSpan.tag(e.getKey(), e.getValue()));
		if (this.startTimestamp != null) {
			nextSpan.start(this.startTimestamp);
		}
		else {
			nextSpan.start();
		}
		return new BraveSpan(nextSpan, this.name);
	}

	private brave.Span nextSpan() {
		if (this.parent != null) {
			return this.tracer.newChild(this.parent.unwrap());
		}
		return this.tracer.nextSpan();
	}

}
