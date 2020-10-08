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

package org.springframework.cloud.sleuth.otel.bridge.http;

import java.net.URI;
import java.net.URISyntaxException;

import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.trace.Tracer;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpClientRequest;
import org.springframework.cloud.sleuth.api.http.HttpClientResponse;
import org.springframework.cloud.sleuth.otel.bridge.OtelSpan;
import org.springframework.cloud.sleuth.otel.bridge.OtelTraceContext;

public class OtelHttpClientHandler extends HttpClientTracer<HttpClientRequest, HttpClientRequest, HttpClientResponse>
		implements HttpClientHandler {

	public OtelHttpClientHandler(Tracer tracer) {
		super(tracer);
	}

	@Override
	public Span handleSend(HttpClientRequest request) {
		io.opentelemetry.trace.Span span = startSpan(request);
		return span(request, span);
	}

	@Override
	public Span handleSendWithParent(HttpClientRequest request, TraceContext parent) {
		io.opentelemetry.trace.Span span = parent != null ? ((OtelTraceContext) parent).span() : null;
		if (span == null) {
			return span(request, startSpan(request));
		}
		try (Scope scope = this.tracer.withSpan(span)) {
			io.opentelemetry.trace.Span withParent = startSpan(request);
			return span(request, withParent);
		}
	}

	private Span span(HttpClientRequest request, io.opentelemetry.trace.Span span) {
		try (Scope scope2 = startScope(span, request)) {
			return OtelSpan.fromOtel(span);
		}
	}

	@Override
	public Span handleSend(HttpClientRequest request, Span span) {
		io.opentelemetry.trace.Span otelSpan = OtelSpan.toOtel(span);
		return span(request, otelSpan);
	}

	@Override
	public void handleReceive(HttpClientResponse response, Span span) {
		io.opentelemetry.trace.Span otel = OtelSpan.toOtel(span);
		if (response.error() != null) {
			endExceptionally(otel, response, response.error());
		}
		else {
			end(otel, response);
		}
	}

	@Override
	protected String method(HttpClientRequest httpClientRequest) {
		return httpClientRequest.method();
	}

	@Override
	protected URI url(HttpClientRequest httpClientRequest) throws URISyntaxException {
		return URI.create(httpClientRequest.url());
	}

	@Override
	protected Integer status(HttpClientResponse httpClientResponse) {
		return httpClientResponse.statusCode();
	}

	@Override
	protected String requestHeader(HttpClientRequest httpClientRequest, String s) {
		return httpClientRequest.header(s);
	}

	@Override
	protected String responseHeader(HttpClientResponse httpClientResponse, String s) {
		return httpClientResponse.header(s);
	}

	@Override
	protected TextMapPropagator.Setter<HttpClientRequest> getSetter() {
		return HttpClientRequest::header;
	}

	@Override
	protected String getInstrumentationName() {
		return "org.springframework.cloud.sleuth";
	}

}
