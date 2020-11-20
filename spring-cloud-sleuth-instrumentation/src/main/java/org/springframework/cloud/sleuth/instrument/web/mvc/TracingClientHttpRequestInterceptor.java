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

package org.springframework.cloud.sleuth.instrument.web.mvc;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpClientRequest;
import org.springframework.cloud.sleuth.http.HttpClientResponse;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.client.HttpStatusCodeException;

public final class TracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

	private static final Log log = LogFactory.getLog(TracingClientHttpRequestInterceptor.class);

	public static ClientHttpRequestInterceptor create(CurrentTraceContext currentTraceContext,
			HttpClientHandler httpClientHandler) {
		return new TracingClientHttpRequestInterceptor(currentTraceContext, httpClientHandler);
	}

	final CurrentTraceContext currentTraceContext;

	final HttpClientHandler handler;

	@Autowired
	TracingClientHttpRequestInterceptor(CurrentTraceContext currentTraceContext, HttpClientHandler httpClientHandler) {
		this.currentTraceContext = currentTraceContext;
		this.handler = httpClientHandler;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest req, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		HttpRequestWrapper request = new HttpRequestWrapper(req);
		Span span = handler.handleSend(request);
		if (log.isDebugEnabled()) {
			log.debug("Wrapping an outbound http call with span [" + span + "]");
		}
		ClientHttpResponse response = null;
		Throwable error = null;
		try (CurrentTraceContext.Scope ws = currentTraceContext.newScope(span.context())) {
			response = execution.execute(req, body);
			return response;
		}
		catch (Throwable e) {
			error = e;
			throw e;
		}
		finally {
			handler.handleReceive(new ClientHttpResponseWrapper(request, response, error), span);
		}
	}

	static final class HttpRequestWrapper implements HttpClientRequest {

		final HttpRequest delegate;

		HttpRequestWrapper(HttpRequest delegate) {
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
		public String method() {
			return delegate.getMethod().name();
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
			Object result = delegate.getHeaders().getFirst(name);
			return result != null ? result.toString() : null;
		}

		@Override
		public void header(String name, String value) {
			delegate.getHeaders().set(name, value);
		}

	}

	static final class ClientHttpResponseWrapper implements HttpClientResponse {

		final HttpRequestWrapper request;

		@Nullable
		final ClientHttpResponse response;

		@Nullable
		final Throwable error;

		ClientHttpResponseWrapper(HttpRequestWrapper request, @Nullable ClientHttpResponse response,
				@Nullable Throwable error) {
			this.request = request;
			this.response = response;
			this.error = error;
		}

		@Override
		public Object unwrap() {
			return response;
		}

		@Override
		public Collection<String> headerNames() {
			return this.response != null ? this.response.getHeaders().keySet() : Collections.emptyList();
		}

		@Override
		public HttpRequestWrapper request() {
			return request;
		}

		@Override
		public Throwable error() {
			return error;
		}

		@Override
		public int statusCode() {
			try {
				int result = response != null ? response.getRawStatusCode() : 0;
				if (result <= 0 && error instanceof HttpStatusCodeException) {
					result = ((HttpStatusCodeException) error).getRawStatusCode();
				}
				return result;
			}
			catch (Exception e) {
				return 0;
			}
		}

	}

}
