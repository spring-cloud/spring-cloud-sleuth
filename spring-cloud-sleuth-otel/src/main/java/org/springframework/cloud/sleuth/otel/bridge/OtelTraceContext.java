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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.grpc.Context;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.TraceState;
import io.opentelemetry.trace.TracingContextUtils;

import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.lang.Nullable;

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
	public long traceIdHigh() {
		// TODO: [OTEL] Double check if this is fine
		return Long.parseLong(this.delegate.getTraceId().toLowerBase16());
	}

	@Override
	public long traceId() {
		// TODO: [OTEL] Double check if this is fine
		return Long.parseLong(this.delegate.getTraceId().toLowerBase16());
	}

	@Override
	public long localRootId() {
		// TODO: [OTEL] Doesn't have a notion of this
		return 0L;
	}

	@Override
	public boolean isLocalRoot() {
		return this.delegate.getTraceId().toLowerBase16().equalsIgnoreCase(this.delegate.getSpanId().toLowerBase16());
	}

	@Override
	public Long parentId() {
		// TODO: [OTEL] Can't do it
		return 0L;
	}

	@Override
	public long parentIdAsLong() {
		// TODO: [OTEL] Can't do it
		return 0L;
	}

	@Override
	public long spanId() {
		return Long.parseLong(this.delegate.getSpanId().toLowerBase16());
	}

	@Override
	public boolean shared() {
		// TODO: [OTEL] Doesn't have a notion of this
		return false;
	}

	@Override
	public List<Object> extra() {
		// TODO: [OTEL] Doesn't have a notion of this
		return Collections.emptyList();
	}

	@Override
	@Nullable
	public <T> T findExtra(Class<T> type) {
		// TODO: [OTEL] Doesn't have a notion of this
		return null;
	}

	@Override
	public Builder toBuilder() {
		return new OtelTraceContextBuilder(this.delegate);
	}

	@Override
	public String traceIdString() {
		return this.delegate.getTraceId().toLowerBase16();
	}

	@Override
	@Nullable
	public String parentIdString() {
		// TODO: [OTEL] Doesn't have a notion of this
		return null;
	}

	@Override
	@Nullable
	public String localRootIdString() {
		// TODO: [OTEL] Doesn't have a notion of this
		return null;
	}

	@Override
	public String spanIdString() {
		return this.delegate.getSpanId().toLowerBase16();
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
		return this.delegate.getTraceFlags().isSampled();
	}

	@Override
	public boolean sampledLocal() {
		// TODO: [OTEL] Doesn't have a notion of this
		return false;
	}

	@Override
	public boolean debug() {
		// TODO: [OTEL] Doesn't have a notion of this
		return false;
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

class OtelTraceContextBuilder implements TraceContext.Builder {

	TraceId traceId;

	SpanId spanId;

	TraceFlags traceFlags;

	TraceState traceState;

	OtelTraceContextBuilder(SpanContext delegate) {
		this.traceId = delegate.getTraceId();
		this.spanId = delegate.getSpanId();
		this.traceFlags = delegate.getTraceFlags();
		this.traceState = delegate.getTraceState();
	}

	@Override
	public TraceContext.Builder traceIdHigh(long traceIdHigh) {
		// TODO: [OTEL] Is this correct?
		this.traceId = new TraceId(traceIdHigh, 0L);
		return this;
	}

	@Override
	public TraceContext.Builder traceId(long traceId) {
		// TODO: [OTEL] Is this correct?
		this.traceId = new TraceId(0L, traceId);
		return this;
	}

	@Override
	public TraceContext.Builder parentId(long parentId) {
		// TODO: [OTEL] Not supported
		return this;
	}

	@Override
	public TraceContext.Builder parentId(Long parentId) {
		// TODO: [OTEL] Not supported
		return this;
	}

	@Override
	public TraceContext.Builder spanId(long spanId) {
		this.spanId = new SpanId(spanId);
		return this;
	}

	@Override
	public TraceContext.Builder sampledLocal(boolean sampledLocal) {
		// TODO: [OTEL] Not supported
		return this;
	}

	@Override
	public TraceContext.Builder sampled(boolean sampled) {
		this.traceFlags = TraceFlags.builder().setIsSampled(sampled).build();
		return this;
	}

	@Override
	public TraceContext.Builder sampled(Boolean sampled) {
		this.traceFlags = TraceFlags.builder().setIsSampled(sampled).build();
		return this;
	}

	@Override
	public TraceContext.Builder debug(boolean debug) {
		// TODO: [OTEL] Not supported
		return this;
	}

	@Override
	public TraceContext.Builder shared(boolean shared) {
		// TODO: [OTEL] Not supported
		return this;
	}

	@Override
	public TraceContext.Builder clearExtra() {
		// TODO: [OTEL] Not supported
		return this;
	}

	@Override
	public TraceContext.Builder addExtra(Object extra) {
		// TODO: [OTEL] Not supported
		return this;
	}

	@Override
	public TraceContext build() {
		return new OtelTraceContext(SpanContext.create(this.traceId, this.spanId, this.traceFlags, this.traceState),
				null);
	}

}