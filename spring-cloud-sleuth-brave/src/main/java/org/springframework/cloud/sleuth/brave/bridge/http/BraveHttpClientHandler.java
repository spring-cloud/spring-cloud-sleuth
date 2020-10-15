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

package org.springframework.cloud.sleuth.brave.bridge.http;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpClientRequest;
import org.springframework.cloud.sleuth.api.http.HttpClientResponse;
import org.springframework.cloud.sleuth.brave.bridge.BraveSpan;
import org.springframework.cloud.sleuth.brave.bridge.BraveTraceContext;

public class BraveHttpClientHandler implements HttpClientHandler {

	final brave.http.HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> delegate;

	public BraveHttpClientHandler(
			brave.http.HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> delegate) {
		this.delegate = delegate;
	}

	@Override
	public Span handleSend(HttpClientRequest request) {
		return BraveSpan.fromBrave(this.delegate.handleSend(BraveHttpClientRequest.toBrave(request)));
	}

	@Override
	public Span handleSend(HttpClientRequest request, TraceContext parent) {
		return BraveSpan.fromBrave(this.delegate.handleSendWithParent(BraveHttpClientRequest.toBrave(request),
				BraveTraceContext.toBrave(parent)));
	}

	@Override
	public void handleReceive(HttpClientResponse response, Span span) {
		this.delegate.handleReceive(BraveHttpClientResponse.toBrave(response), BraveSpan.toBrave(span));
	}

	public static HttpClientHandler fromBrave(
			brave.http.HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler) {
		return new BraveHttpClientHandler(handler);
	}

}
