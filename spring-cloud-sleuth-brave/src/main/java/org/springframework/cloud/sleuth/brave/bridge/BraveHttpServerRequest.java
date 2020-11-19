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

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.http.HttpServerRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Brave implementation of a {@link HttpServerRequest}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class BraveHttpServerRequest implements HttpServerRequest {

	final brave.http.HttpServerRequest delegate;

	BraveHttpServerRequest(brave.http.HttpServerRequest delegate) {
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

	static brave.http.HttpServerRequest toBrave(HttpServerRequest request) {
		if (request == null) {
			return null;
		}
		if (request instanceof BraveHttpServerRequest) {
			return ((BraveHttpServerRequest) request).delegate;
		}
		return new brave.http.HttpServerRequest() {

			@Override
			public Object unwrap() {
				return request.unwrap();
			}

			@Override
			public String method() {
				return request.method();
			}

			@Override
			public String path() {
				return request.path();
			}

			@Override
			public String url() {
				return request.url();
			}

			@Override
			public String header(String name) {
				return request.header(name);
			}

			@Override
			public boolean parseClientIpAndPort(brave.Span span) {
				boolean clientIpAndPortParsed = super.parseClientIpAndPort(span);
				if (clientIpAndPortParsed) {
					return true;
				}
				return resolveFromInetAddress(span);
			}

			private boolean resolveFromInetAddress(brave.Span span) {
				Object delegate = request.unwrap();
				if (delegate instanceof ServerHttpRequest) {
					InetSocketAddress addr = ((ServerHttpRequest) delegate).getRemoteAddress();
					if (addr == null) {
						return false;
					}
					return span.remoteIpAndPort(addr.getAddress().getHostAddress(), addr.getPort());
				}
				return false;
			}

		};
	}

}
