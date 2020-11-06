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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.instrument.web.mvc.TracingClientHttpRequestInterceptor.ClientHttpResponseWrapper;
import org.springframework.cloud.sleuth.instrument.web.mvc.TracingClientHttpRequestInterceptor.HttpRequestWrapper;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.AsyncClientHttpRequestExecution;
import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

public final class TracingAsyncClientHttpRequestInterceptor implements AsyncClientHttpRequestInterceptor {

	public static AsyncClientHttpRequestInterceptor create(CurrentTraceContext currentTraceContext,
			HttpClientHandler httpClientHandler) {
		return new TracingAsyncClientHttpRequestInterceptor(currentTraceContext, httpClientHandler);
	}

	final CurrentTraceContext currentTraceContext;

	final HttpClientHandler handler;

	@Autowired
	TracingAsyncClientHttpRequestInterceptor(CurrentTraceContext currentTraceContext,
			HttpClientHandler httpClientHandler) {
		this.currentTraceContext = currentTraceContext;
		this.handler = httpClientHandler;
	}

	@Override
	public ListenableFuture<ClientHttpResponse> intercept(HttpRequest req, byte[] body,
			AsyncClientHttpRequestExecution execution) throws IOException {
		HttpRequestWrapper request = new HttpRequestWrapper(req);
		Span span = handler.handleSend(request);

		// avoid context sync overhead when we are the root span
		String parentId = span.context().parentId();
		TraceContext invocationContext = parentId != null ? currentTraceContext.context() : null;

		try (CurrentTraceContext.Scope ws = currentTraceContext.maybeScope(span.context())) {
			ListenableFuture<ClientHttpResponse> result = execution.executeAsync(req, body);
			result.addCallback(new TraceListenableFutureCallback(request, span, handler));
			return invocationContext != null
					? new TraceContextListenableFuture<>(result, currentTraceContext, invocationContext) : result;
		}
		catch (Throwable e) {
			handler.handleReceive(new ClientHttpResponseWrapper(request, null, e), span);
			throw e;
		}
	}

	static final class TraceListenableFutureCallback implements ListenableFutureCallback<ClientHttpResponse> {

		final HttpRequestWrapper request;

		final Span span;

		final HttpClientHandler handler;

		TraceListenableFutureCallback(HttpRequestWrapper request, Span span, HttpClientHandler handler) {
			this.request = request;
			this.span = span;
			this.handler = handler;
		}

		@Override
		public void onFailure(Throwable ex) {
			handler.handleReceive(new ClientHttpResponseWrapper(request, null, ex), span);
		}

		@Override
		public void onSuccess(ClientHttpResponse response) {
			handler.handleReceive(new ClientHttpResponseWrapper(request, response, null), span);
		}

	}

}
