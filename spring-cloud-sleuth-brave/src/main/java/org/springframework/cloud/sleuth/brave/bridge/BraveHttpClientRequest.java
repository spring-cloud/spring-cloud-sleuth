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

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.http.HttpClientRequest;

/**
 * Brave implementation of a {@link HttpClientRequest}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class BraveHttpClientRequest implements HttpClientRequest {

	final brave.http.HttpClientRequest delegate;

	BraveHttpClientRequest(brave.http.HttpClientRequest delegate) {
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
	public void header(String name, String value) {
		this.delegate.header(name, value);
	}

	@Override
	public String path() {
		return this.delegate.path();
	}

	@Override
	public String url() {
		return this.delegate.url();
	}

	@Override
	public String header(String name) {
		return this.delegate.header(name);
	}

	static brave.http.HttpClientRequest toBrave(HttpClientRequest httpClientRequest) {
		if (httpClientRequest instanceof BraveHttpClientRequest) {
			return ((BraveHttpClientRequest) httpClientRequest).delegate;
		}
		return new brave.http.HttpClientRequest() {

			@Override
			public Object unwrap() {
				return httpClientRequest.unwrap();
			}

			@Override
			public String method() {
				return httpClientRequest.method();
			}

			@Override
			public String path() {
				return httpClientRequest.path();
			}

			@Override
			public String url() {
				return httpClientRequest.url();
			}

			@Override
			public String header(String name) {
				return httpClientRequest.header(name);
			}

			@Override
			public void header(String name, String value) {
				httpClientRequest.header(name, value);
			}
		};
	}

}
