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

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.SamplerFunction;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpRequest;
import org.springframework.cloud.sleuth.http.HttpRequestParser;
import org.springframework.cloud.sleuth.http.HttpResponseParser;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

public final class OtelAccessor {

	private OtelAccessor() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	public static Tracer tracer(io.opentelemetry.api.trace.Tracer tracer, CurrentTraceContext currentTraceContext,
			SleuthBaggageProperties sleuthBaggageProperties, ApplicationEventPublisher publisher) {
		return new OtelTracer(tracer, publisher, new OtelBaggageManager(currentTraceContext,
				sleuthBaggageProperties.getRemoteFields(), sleuthBaggageProperties.getTagFields(), publisher));
	}

	public static CurrentTraceContext currentTraceContext(ApplicationEventPublisher publisher) {
		return new OtelCurrentTraceContext(publisher);
	}

	public static CurrentTraceContext currentTraceContext() {
		return currentTraceContext(event -> {
		});
	}

	public static TraceContext traceContext(SpanContext spanContext) {
		return OtelTraceContext.fromOtel(spanContext);
	}

	public static Propagator propagator(ContextPropagators propagators, io.opentelemetry.api.trace.Tracer tracer) {
		return new OtelPropagator(propagators, tracer);
	}

	public static HttpClientHandler httpClientHandler(io.opentelemetry.api.trace.Tracer tracer,
			@Nullable HttpRequestParser httpClientRequestParser, @Nullable HttpResponseParser httpClientResponseParser,
			SamplerFunction<HttpRequest> samplerFunction) {
		return new OtelHttpClientHandler(tracer, httpClientRequestParser, httpClientResponseParser, samplerFunction);
	}

	public static HttpServerHandler httpServerHandler(io.opentelemetry.api.trace.Tracer tracer,
			HttpRequestParser httpServerRequestParser, HttpResponseParser httpServerResponseParser,
			SkipPatternProvider skipPatternProvider) {
		return new OtelHttpServerHandler(tracer, httpServerRequestParser, httpServerResponseParser,
				skipPatternProvider);
	}

	public static FinishedSpan finishedSpan(SpanData spanData) {
		return new OtelFinishedSpan(spanData);
	}

}
