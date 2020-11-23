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

package org.springframework.cloud.sleuth.brave.bridge;

import brave.Tracing;
import brave.handler.MutableSpan;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpRequestParser;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.propagation.Propagator;

public final class BraveAccessor {

	private BraveAccessor() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	public static Tracer tracer(brave.Tracer braveTracer) {
		return new BraveTracer(braveTracer, new BraveBaggageManager());
	}

	public static CurrentTraceContext currentTraceContext(brave.propagation.CurrentTraceContext context) {
		return BraveCurrentTraceContext.fromBrave(context);
	}

	public static TraceContext traceContext(brave.propagation.TraceContext traceContext) {
		return BraveTraceContext.fromBrave(traceContext);
	}

	public static brave.propagation.TraceContext traceContext(TraceContext traceContext) {
		return BraveTraceContext.toBrave(traceContext);
	}

	public static brave.Span braveSpan(Span span) {
		return BraveSpan.toBrave(span);
	}

	public static Propagator propagator(Tracing tracing) {
		return new BravePropagator(tracing);
	}

	public static HttpClientHandler httpClientHandler(
			brave.http.HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> delegate) {
		return new BraveHttpClientHandler(delegate);
	}

	public static HttpServerHandler httpServerHandler(
			brave.http.HttpServerHandler<brave.http.HttpServerRequest, brave.http.HttpServerResponse> delegate) {
		return new BraveHttpServerHandler(delegate);
	}

	public static brave.http.HttpRequestParser httpRequestParser(HttpRequestParser delegate) {
		return BraveHttpRequestParser.toBrave(delegate);
	}

	public static FinishedSpan finishedSpan(MutableSpan mutableSpan) {
		return new BraveFinishedSpan(mutableSpan);
	}

}
