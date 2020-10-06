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

import io.grpc.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.http.HttpRequest;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.api.http.HttpServerRequest;
import org.springframework.cloud.sleuth.api.http.HttpServerResponse;
import org.springframework.cloud.sleuth.otel.bridge.OtelSpan;

public class OtelHttpServerHandler
		extends HttpServerTracer<HttpServerRequest, HttpServerResponse, HttpServerRequest, HttpServerRequest>
		implements HttpServerHandler {

	@Override
	public Span handleReceive(HttpServerRequest request) {
		return OtelSpan.fromOtel(startSpan(request, request, request.method()));
	}

	@Override
	public void handleSend(HttpServerResponse response, Span span) {
		Throwable throwable = response.error();
		if (throwable == null) {
			end(OtelSpan.toOtel(span), response);
		}
		else {
			endExceptionally(OtelSpan.toOtel(span), throwable, response);
		}
	}

	@Override
	public Context getServerContext(HttpServerRequest request) {
		Object context = request.getAttribute(CONTEXT_ATTRIBUTE);
		return context instanceof Context ? (Context) context : null;
	}

	@Override
	protected Integer peerPort(HttpServerRequest request) {
		return url(request).getPort();
	}

	@Override
	protected String peerHostIP(HttpServerRequest request) {
		return url(request).getHost();
	}

	@Override
	protected String flavor(HttpServerRequest request, HttpServerRequest request2) {
		return url(request).getScheme();
	}

	@Override
	protected TextMapPropagator.Getter<HttpServerRequest> getGetter() {
		return HttpRequest::header;
	}

	@Override
	protected URI url(HttpServerRequest request) {
		return URI.create(request.url());
	}

	@Override
	protected String method(HttpServerRequest request) {
		return request.method();
	}

	@Override
	protected String requestHeader(HttpServerRequest request, String s) {
		return request.header(s);
	}

	@Override
	protected int responseStatus(HttpServerResponse httpServerResponse) {
		return httpServerResponse.statusCode();
	}

	@Override
	protected void attachServerContext(Context context, HttpServerRequest request) {
		request.setAttribute(CONTEXT_ATTRIBUTE, context);
	}

	@Override
	protected String getInstrumentationName() {
		return "org.springframework.cloud.sleuth";
	}

}
