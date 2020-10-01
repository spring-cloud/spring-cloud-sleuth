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

import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;

import org.springframework.cloud.sleuth.api.dunno.CarrierHandler;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.brave.bridge.BraveSpan;

public class BraveHttpServerHandler implements CarrierHandler<HttpServerRequest, HttpServerResponse> {

	final HttpServerHandler delegate;

	public BraveHttpServerHandler(HttpServerHandler delegate) {
		this.delegate = delegate;
	}

	@Override
	public Span handleReceive(HttpServerRequest input) {
		return BraveSpan.fromBrave(this.delegate.handleReceive(input));
	}

	@Override
	public void handleSend(HttpServerResponse output, Span span) {
		this.delegate.handleSend(output, BraveSpan.toBrave(span));
	}

	public static HttpServerHandler<HttpServerRequest, HttpServerResponse> toBrave(CarrierHandler<HttpServerRequest, HttpServerResponse> carrier) {
		return ((BraveHttpServerHandler) carrier).delegate;
	}
}
