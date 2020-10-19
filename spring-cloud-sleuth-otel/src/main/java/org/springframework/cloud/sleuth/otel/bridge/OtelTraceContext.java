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

import io.grpc.Context;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.TracingContextUtils;

import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.lang.Nullable;

/**
 * OpenTelemetry implementation of a {@link TraceContext}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class OtelTraceContext implements TraceContext {

	final SpanContext delegate;

	final Span span;

	public OtelTraceContext(SpanContext delegate, @Nullable Span span) {
		this.delegate = delegate;
		this.span = span;
	}

	public OtelTraceContext(Span span) {
		this(span.getContext(), span);
	}

	@Override
	public String traceId() {
		return this.delegate.getTraceIdAsHexString();
	}

	@Override
	@Nullable
	public String parentId() {
		if (this.span instanceof ReadableSpan) {
			ReadableSpan readableSpan = (ReadableSpan) this.span;
			return readableSpan.toSpanData().getParentSpanId();
		}
		return null;
	}

	@Override
	public String spanId() {
		return this.delegate.getSpanIdAsHexString();
	}

	@Override
	public String toString() {
		return this.delegate != null ? this.delegate.toString() : "null";
	}

	@Override
	public boolean equals(Object o) {
		return Objects.equals(this.delegate, o);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.delegate);
	}

	@Nullable
	public Boolean sampled() {
		return this.delegate.isSampled();
	}

	public Span span() {
		return this.span;
	}

	public SpanContext spanContext() {
		return this.delegate;
	}

	public static SpanContext toOtel(TraceContext traceContext) {
		if (traceContext == null) {
			return null;
		}
		return ((OtelTraceContext) traceContext).delegate;
	}

	public static TraceContext fromOtel(SpanContext traceContext) {
		return new OtelTraceContext(traceContext, null);
	}

	public static Context toOtelContext(TraceContext context) {
		if (context instanceof OtelTraceContext) {
			Span span = ((OtelTraceContext) context).span;
			if (span != null) {
				return TracingContextUtils.withSpan(span, Context.current());
			}
		}
		return Context.current();
	}

}
