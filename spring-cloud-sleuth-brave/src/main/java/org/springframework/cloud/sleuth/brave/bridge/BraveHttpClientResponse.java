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

import java.util.Collection;
import java.util.Collections;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.http.HttpClientRequest;
import org.springframework.cloud.sleuth.api.http.HttpClientResponse;

/**
 * Brave implementation of a {@link HttpClientResponse}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class BraveHttpClientResponse implements HttpClientResponse {

	final brave.http.HttpClientResponse delegate;

	BraveHttpClientResponse(brave.http.HttpClientResponse delegate) {
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
	public Collection<String> headerNames() {
		// this is unused by Brave
		return Collections.emptyList();
	}

	@Override
	public Span.Kind spanKind() {
		return Span.Kind.valueOf(this.delegate.spanKind().name());
	}

	@Override
	public HttpClientRequest request() {
		brave.http.HttpClientRequest request = this.delegate.request();
		if (request == null) {
			return null;
		}
		return new BraveHttpClientRequest(request);
	}

	@Override
	public Throwable error() {
		return this.delegate.error();
	}

	public static brave.http.HttpClientResponse toBrave(HttpClientResponse httpClientResponse) {
		if (httpClientResponse == null) {
			return null;
		}
		else if (httpClientResponse instanceof BraveHttpClientResponse) {
			return ((BraveHttpClientResponse) httpClientResponse).delegate;
		}
		return new brave.http.HttpClientResponse() {
			@Override
			public int statusCode() {
				return httpClientResponse.statusCode();
			}

			@Override
			public Object unwrap() {
				return httpClientResponse.unwrap();
			}

			@Override
			public brave.http.HttpClientRequest request() {
				return BraveHttpClientRequest.toBrave(httpClientResponse.request());
			}

			@Override
			public Throwable error() {
				return httpClientResponse.error();
			}

			@Override
			public String method() {
				return httpClientResponse.method();
			}

			@Override
			public String route() {
				return httpClientResponse.route();
			}
		};
	}

}
