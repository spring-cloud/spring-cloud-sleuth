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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.Collection;
import java.util.List;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpClientRequest;
import org.springframework.cloud.sleuth.http.HttpClientResponse;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

abstract class AbstractHttpHeadersFilter implements HttpHeadersFilter {

	static final String SPAN_ATTRIBUTE = Span.class.getName();

	final Tracer tracer;

	final HttpClientHandler handler;

	final Propagator propagator;

	AbstractHttpHeadersFilter(Tracer tracer, HttpClientHandler handler, Propagator propagator) {
		this.tracer = tracer;
		this.propagator = propagator;
		this.handler = handler;
	}

	static final class ServerHttpClientRequest implements HttpClientRequest {

		final ServerHttpRequest delegate;

		final HttpHeaders filteredHeaders;

		ServerHttpClientRequest(ServerHttpRequest delegate, HttpHeaders filteredHeaders) {
			this.delegate = delegate;
			this.filteredHeaders = filteredHeaders;
		}

		@Override
		public Collection<String> headerNames() {
			return this.delegate.getHeaders().keySet();
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public String method() {
			return delegate.getMethodValue();
		}

		@Override
		public String path() {
			return delegate.getURI().getPath();
		}

		@Override
		public String url() {
			return delegate.getURI().toString();
		}

		@Override
		public String header(String name) {
			return filteredHeaders.getFirst(name);
		}

		@Override
		public void header(String name, String value) {
			filteredHeaders.set(name, value);
		}

	}

	static final class ServerHttpClientResponse implements HttpClientResponse {

		final ServerHttpResponse delegate;

		ServerHttpClientResponse(ServerHttpResponse delegate) {
			this.delegate = delegate;
		}

		@Override
		public Collection<String> headerNames() {
			return this.delegate.getHeaders().keySet();
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public int statusCode() {
			return delegate.getStatusCode() != null ? delegate.getStatusCode().value() : 0;
		}

		@Override
		public String header(String header) {
			List<String> headers = delegate.getHeaders().get(header);
			if (headers == null || headers.isEmpty()) {
				return null;
			}
			return headers.get(0);
		}

	}

}
