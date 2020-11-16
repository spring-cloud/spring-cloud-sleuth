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

import java.util.List;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.propagation.Propagator;

/**
 * OpenTelemetry implementation of a {@link Propagator}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class OtelPropagator implements Propagator {

	private final TextMapPropagator propagator;

	private final Tracer tracer;

	OtelPropagator(ContextPropagators propagation, Tracer tracer) {
		this.propagator = propagation.getTextMapPropagator();
		this.tracer = tracer;
	}

	@Override
	public List<String> fields() {
		return this.propagator.fields();
	}

	@Override
	public <C> void inject(TraceContext traceContext, C carrier, Setter<C> setter) {
		Context context = OtelTraceContext.toOtelContext(traceContext);
		this.propagator.inject(context, carrier, setter::set);
	}

	@Override
	public <C> Span.Builder extract(C carrier, Getter<C> getter) {
		Context extracted = this.propagator.extract(Context.current(), carrier, new TextMapPropagator.Getter<C>() {
			@Override
			public Iterable<String> keys(C carrier) {
				return fields();
			}

			@Override
			public String get(C carrier, String key) {
				return getter.get(carrier, key);
			}
		});
		io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.fromContextOrNull(extracted);
		if (span == null || span.equals(io.opentelemetry.api.trace.Span.getInvalid())) {
			return OtelSpanBuilder.fromOtel(tracer.spanBuilder(""));
		}
		return OtelSpanBuilder.fromOtel(this.tracer.spanBuilder("").setParent(extracted));
	}

}
