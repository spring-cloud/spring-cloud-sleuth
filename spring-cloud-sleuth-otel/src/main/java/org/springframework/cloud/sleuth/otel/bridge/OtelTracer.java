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

import java.util.Map;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.api.BaggageInScope;
import org.springframework.cloud.sleuth.api.ScopedSpan;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.Tracer;

/**
 * OpenTelemetry implementation of a {@link Tracer}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class OtelTracer implements Tracer {

	private final io.opentelemetry.api.trace.Tracer tracer;

	private final OtelBaggageManager otelBaggageManager;

	public OtelTracer(io.opentelemetry.api.trace.Tracer tracer, OtelBaggageManager otelBaggageManager) {
		this.tracer = tracer;
		this.otelBaggageManager = otelBaggageManager;
	}

	public static Tracer fromOtel(io.opentelemetry.api.trace.Tracer tracer, OtelBaggageManager otelBaggageManager) {
		return new OtelTracer(tracer, otelBaggageManager);
	}

	@Override
	public Span nextSpan(Span parent) {
		if (parent == null) {
			return nextSpan();
		}
		return OtelSpan.fromOtel(
				this.tracer.spanBuilder("").setParent(OtelTraceContext.toOtelContext(parent.context())).startSpan());
	}

	@Override
	public SpanInScope withSpan(Span span) {
		io.opentelemetry.api.trace.Span delegate = span == null ? io.opentelemetry.api.trace.Span.getInvalid()
				: ((OtelSpan) span).delegate;
		return new OtelSpanInScope((OtelSpan) span, delegate);
	}

	@Override
	public SpanCustomizer currentSpanCustomizer() {
		return new OtelSpanCustomizer(this.tracer);
	}

	@Override
	public Span currentSpan() {
		io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
		if (currentSpan == null || currentSpan.equals(io.opentelemetry.api.trace.Span.getInvalid())) {
			return null;
		}
		return new OtelSpan(currentSpan);
	}

	@Override
	public Span nextSpan() {
		return new OtelSpan(this.tracer.spanBuilder("").startSpan());
	}

	@Override
	public ScopedSpan startScopedSpan(String name) {
		io.opentelemetry.api.trace.Span span = this.tracer.spanBuilder(name).startSpan();
		return new OtelScopedSpan(span, span.makeCurrent());
	}

	@Override
	public Span.Builder spanBuilder() {
		return new OtelSpanBuilder(this.tracer.spanBuilder(""));
	}

	@Override
	public Map<String, String> getAllBaggage() {
		return this.otelBaggageManager.getAllBaggage();
	}

	@Override
	public BaggageInScope getBaggage(String name) {
		return this.otelBaggageManager.getBaggage(name);
	}

	@Override
	public BaggageInScope getBaggage(TraceContext traceContext, String name) {
		return this.otelBaggageManager.getBaggage(traceContext, name);
	}

	@Override
	public BaggageInScope createBaggage(String name) {
		return this.otelBaggageManager.createBaggage(name);
	}

	@Override
	public BaggageInScope createBaggage(String name, String value) {
		return this.otelBaggageManager.createBaggage(name, value);
	}

}

class OtelSpanInScope implements Tracer.SpanInScope {

	private static final Log log = LogFactory.getLog(OtelSpanInScope.class);

	final Scope delegate;

	final OtelSpan sleuthSpan;

	final io.opentelemetry.api.trace.Span otelSpan;

	final SpanContext spanContext;

	OtelSpanInScope(OtelSpan sleuthSpan, io.opentelemetry.api.trace.Span otelSpan) {
		this.sleuthSpan = sleuthSpan;
		this.otelSpan = otelSpan;
		this.delegate = otelSpan.makeCurrent();
		this.spanContext = otelSpan.getSpanContext();
	}

	@Override
	public void close() {
		if (log.isTraceEnabled()) {
			log.trace("Will close scope for trace context [" + this.spanContext + "]");
		}
		this.delegate.close();
	}

}
