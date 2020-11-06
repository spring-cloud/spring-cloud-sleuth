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

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.api.SamplerFunction;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpClientRequest;
import org.springframework.cloud.sleuth.api.http.HttpClientResponse;
import org.springframework.cloud.sleuth.api.http.HttpRequest;
import org.springframework.cloud.sleuth.api.http.HttpRequestParser;
import org.springframework.cloud.sleuth.api.http.HttpResponseParser;
import org.springframework.cloud.sleuth.otel.bridge.OtelSpan;
import org.springframework.cloud.sleuth.otel.bridge.OtelTraceContext;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * OpenTelemetry implementation of a {@link HttpClientHandler}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class OtelHttpClientHandler extends HttpClientTracer<HttpClientRequest, HttpClientRequest, HttpClientResponse>
		implements HttpClientHandler {

	private static final Log log = LogFactory.getLog(OtelHttpClientHandler.class);

	private final HttpRequestParser httpClientRequestParser;

	private final HttpResponseParser httpClientResponseParser;

	private final SamplerFunction<HttpRequest> samplerFunction;

	public OtelHttpClientHandler(Tracer tracer, @Nullable HttpRequestParser httpClientRequestParser,
			@Nullable HttpResponseParser httpClientResponseParser, SamplerFunction<HttpRequest> samplerFunction) {
		super(tracer);
		this.httpClientRequestParser = httpClientRequestParser;
		this.httpClientResponseParser = httpClientResponseParser;
		this.samplerFunction = samplerFunction;
	}

	@Override
	public Span handleSend(HttpClientRequest request) {
		if (Boolean.FALSE.equals(this.samplerFunction.trySample(request))) {
			if (log.isDebugEnabled()) {
				log.debug("The sampler function filtered this request, will return an invalid span");
			}
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}
		io.opentelemetry.api.trace.Span span = startSpan(request);
		return span(request, span);
	}

	@Override
	public Span handleSend(HttpClientRequest request, TraceContext parent) {
		if (Boolean.FALSE.equals(this.samplerFunction.trySample(request))) {
			if (log.isDebugEnabled()) {
				log.debug("Returning an invalid span since url [" + request.path() + "] is on a list of urls to skip");
			}
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}
		io.opentelemetry.api.trace.Span span = parent != null ? ((OtelTraceContext) parent).span() : null;
		if (span == null) {
			return span(request, startSpan(request));
		}
		try (Scope scope = span.makeCurrent()) {
			io.opentelemetry.api.trace.Span withParent = startSpan(request);
			return span(request, withParent);
		}
	}

	private Span span(HttpClientRequest request, io.opentelemetry.api.trace.Span span) {
		try (Scope scope = startScope(span, request)) {
			if (span.isRecording()) {
				String remoteIp = request.remoteIp();
				if (StringUtils.hasText(remoteIp)) {
					span.setAttribute("net.peer.ip", remoteIp);
				}
				span.setAttribute("net.peer.port", request.remotePort());
			}
			return OtelSpan.fromOtel(span);
		}
	}

	@Override
	protected io.opentelemetry.api.trace.Span onRequest(io.opentelemetry.api.trace.Span span,
			HttpClientRequest httpClientRequest) {
		io.opentelemetry.api.trace.Span afterRequest = super.onRequest(span, httpClientRequest);
		if (this.httpClientRequestParser != null) {
			Span fromOtel = OtelSpan.fromOtel(afterRequest);
			this.httpClientRequestParser.parse(httpClientRequest, fromOtel.context(), fromOtel);
		}
		String path = httpClientRequest.path();
		if (path != null) {
			span.setAttribute("http.path", path);
		}
		return afterRequest;
	}

	@Override
	protected io.opentelemetry.api.trace.Span onResponse(io.opentelemetry.api.trace.Span span,
			HttpClientResponse httpClientResponse) {
		io.opentelemetry.api.trace.Span afterResponse = super.onResponse(span, httpClientResponse);
		if (this.httpClientResponseParser != null) {
			Span fromOtel = OtelSpan.fromOtel(afterResponse);
			this.httpClientResponseParser.parse(httpClientResponse, fromOtel.context(), fromOtel);
		}
		return afterResponse;
	}

	@Override
	public void handleReceive(HttpClientResponse response, Span span) {
		if (OtelSpan.toOtel(span).equals(io.opentelemetry.api.trace.Span.getInvalid())) {
			if (log.isDebugEnabled()) {
				log.debug("Not doing anything cause the span is invalid");
			}
			return;
		}
		io.opentelemetry.api.trace.Span otel = OtelSpan.toOtel(span);
		if (response.error() != null) {
			if (log.isDebugEnabled()) {
				log.debug("There was an error, will finish span [" + otel + "] exceptionally");
			}
			endExceptionally(otel, response, response.error());
		}
		else {
			if (log.isDebugEnabled()) {
				log.debug("There was no error, will finish span [" + otel + "] in a standard way");
			}
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
