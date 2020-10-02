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
import org.springframework.cloud.sleuth.api.http.HttpServerRequest;
import org.springframework.cloud.sleuth.api.http.HttpServerResponse;

public class BraveHttpServerResponse implements HttpServerResponse {

	final brave.http.HttpServerResponse delegate;

	public BraveHttpServerResponse(brave.http.HttpServerResponse delegate) {
		this.delegate = delegate;
	}
	
	@Override
	public String method() {
		return this.delegate.method();
	}

	@Override
	public String route() {
		return this.delegate.route();
	}

	@Override
	public int statusCode() {
		return this.delegate.statusCode();
	}

	@Override
	public Object unwrap() {
		return this.delegate.unwrap();
	}

	@Override
	public Span.Kind spanKind() {
		return Span.Kind.valueOf(this.delegate.spanKind().name());
	}

	@Override
	public HttpServerRequest request() {
		brave.http.HttpServerRequest request = this.delegate.request();
		if (request == null) {
			return null;
		}
		return new BraveHttpServerRequest(request);
	}

	@Override
	public Throwable error() {
		return this.delegate.error();
	}

	public static brave.http.HttpServerResponse toBrave(HttpServerResponse response) {
		if (response == null) {
			return null;
		} else if (response instanceof BraveHttpServerResponse) {
			return ((BraveHttpServerResponse) response).delegate;
		}
		return new brave.http.HttpServerResponse() {
			@Override
			public brave.http.HttpServerRequest request() {
				return BraveHttpServerRequest.toBrave(response.request());
			}

			@Override
			public Throwable error() {
				return response.error();
			}

			@Override
			public String method() {
				return response.method();
			}

			@Override
			public String route() {
				return response.route();
			}

			@Override
			public String toString() {
				return response.toString();
			}

			@Override
			public int statusCode() {
				return response.statusCode();
			}

			@Override
			public Object unwrap() {
				return response.unwrap();
			}
		};
	}
}
