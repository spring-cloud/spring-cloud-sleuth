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

import java.util.LinkedList;
import java.util.List;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.util.StringUtils;

/**
 * OpenTelemetry implementation of a {@link Span.Builder}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class OtelSpanBuilder implements Span.Builder {

	private final io.opentelemetry.api.trace.Span.Builder delegate;

	private final List<String> annotations = new LinkedList<>();

	private String name;

	private Throwable error;

	public OtelSpanBuilder(io.opentelemetry.api.trace.Span.Builder delegate) {
		this.delegate = delegate;
	}

	public static Span.Builder fromOtel(io.opentelemetry.api.trace.Span.Builder builder) {
		return new OtelSpanBuilder(builder);
	}

	@Override
	public Span.Builder setParent(TraceContext context) {
		this.delegate.setParent(OtelTraceContext.toOtelContext(context));
		return this;
	}

	@Override
	public Span.Builder setNoParent() {
		this.delegate.setNoParent();
		return this;
	}

	@Override
	public Span.Builder name(String name) {
		this.name = name;
		return this;
	}

	@Override
	public Span.Builder event(String value) {
		this.annotations.add(value);
		return this;
	}

	@Override
	public Span.Builder tag(String key, String value) {
		this.delegate.setAttribute(key, value);
		return this;
	}

	@Override
	public Span.Builder error(Throwable throwable) {
		this.error = throwable;
		return this;
	}

	@Override
	public Span.Builder kind(Span.Kind spanKind) {
		if (spanKind == null) {
			this.delegate.setSpanKind(io.opentelemetry.api.trace.Span.Kind.INTERNAL);
			return this;
		}
		io.opentelemetry.api.trace.Span.Kind kind = io.opentelemetry.api.trace.Span.Kind.INTERNAL;
		switch (spanKind) {
		case CLIENT:
			kind = io.opentelemetry.api.trace.Span.Kind.CLIENT;
			break;
		case SERVER:
			kind = io.opentelemetry.api.trace.Span.Kind.SERVER;
			break;
		case PRODUCER:
			kind = io.opentelemetry.api.trace.Span.Kind.PRODUCER;
			break;
		case CONSUMER:
			kind = io.opentelemetry.api.trace.Span.Kind.CONSUMER;
			break;
		}
		this.delegate.setSpanKind(kind);
		return this;
	}

	@Override
	public Span.Builder remoteServiceName(String remoteServiceName) {
		this.delegate.setAttribute("peer.service", remoteServiceName);
		return this;
	}

	@Override
	public Span start() {
		io.opentelemetry.api.trace.Span span = this.delegate.startSpan();
		if (StringUtils.hasText(this.name)) {
			span.updateName(this.name);
		}
		if (this.error != null) {
			span.recordException(error);
		}
		this.annotations.forEach(span::addEvent);
		return OtelSpan.fromOtel(span);
	}

}
