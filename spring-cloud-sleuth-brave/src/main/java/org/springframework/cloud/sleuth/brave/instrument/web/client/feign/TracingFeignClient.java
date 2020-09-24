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

package org.springframework.cloud.sleuth.brave.instrument.web.client.feign;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import brave.Span;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.util.ProxyUtils;
import org.springframework.lang.Nullable;

/**
 * Feign client wrapper.
 *
 * @author Marcin Grzejsczak
 * @since 2.0.0
 */
final class TracingFeignClient implements Client {

	private static final Log log = LogFactory.getLog(TracingFeignClient.class);

	final CurrentTraceContext currentTraceContext;

	final Client delegate;

	final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

	TracingFeignClient(HttpTracing httpTracing, Client delegate) {
		this.currentTraceContext = httpTracing.tracing().currentTraceContext();
		this.handler = HttpClientHandler.create(httpTracing);
		Client delegateTarget = ProxyUtils.getTargetObject(delegate);
		this.delegate = delegateTarget instanceof TracingFeignClient ? ((TracingFeignClient) delegateTarget).delegate
				: delegateTarget;
	}

	static Client create(HttpTracing httpTracing, Client delegate) {
		return new TracingFeignClient(httpTracing, delegate);
	}

	@Override
	public Response execute(Request req, Request.Options options) throws IOException {
		RequestWrapper request = new RequestWrapper(req);
		Span span = this.handler.handleSend(request);
		if (log.isDebugEnabled()) {
			log.debug("Handled send of " + span);
		}
		Response res = null;
		Throwable error = null;
		try (Scope ws = this.currentTraceContext.newScope(span.context())) {
			res = this.delegate.execute(request.build(), options);
			if (res == null) { // possibly null on bad implementation or mocks
				res = Response.builder().request(req).build();
			}
			return res;
		}
		catch (Throwable e) {
			error = e;
			throw e;
		}
		finally {
			ResponseWrapper response = res != null ? new ResponseWrapper(request, res, error) : null;
			this.handler.handleReceive(response, error, span);

			if (log.isDebugEnabled()) {
				log.debug("Handled receive of " + span);
			}
		}
	}

	void handleSendAndReceive(Span span, Request req, @Nullable Response res, @Nullable Throwable error) {
		RequestWrapper request = new RequestWrapper(req);
		this.handler.handleSend(request, span);
		ResponseWrapper response = res != null ? new ResponseWrapper(request, res, error) : null;
		this.handler.handleReceive(response, error, span);
	}

	static final class RequestWrapper extends HttpClientRequest {

		final Request delegate;

		Map<String, Collection<String>> headers;

		RequestWrapper(Request delegate) {
			this.delegate = delegate;
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public String method() {
			return delegate.method();
		}

		@Override
		public String path() {
			String url = url();
			if (url == null) {
				return null;
			}
			return URI.create(url).getPath();
		}

		@Override
		public String url() {
			return delegate.url();
		}

		@Override
		public String header(String name) {
			Collection<String> result = delegate.headers().get(name);
			return result != null && result.iterator().hasNext() ? result.iterator().next() : null;
		}

		@Override
		public void header(String name, String value) {
			if (headers == null) {
				headers = new LinkedHashMap<>(delegate.headers());
			}
			if (!headers.containsKey(name)) {
				headers.put(name, Collections.singletonList(value));
				if (log.isTraceEnabled()) {
					log.trace("Added key [" + name + "] and header value [" + value + "]");
				}
			}
			else {
				// TODO: this is incorrect to ignore as opposed to overwrite!
				if (log.isTraceEnabled()) {
					log.trace("Key [" + name + "] already there in the headers");
				}
			}
		}

		Request build() {
			if (headers == null) {
				return delegate;
			}
			String url = delegate.url();
			byte[] body = delegate.body();
			Charset charset = delegate.charset();
			return Request.create(delegate.httpMethod(), url, headers, body, charset, delegate.requestTemplate());
		}

	}

	static final class ResponseWrapper extends HttpClientResponse {

		final RequestWrapper request;

		final Response response;

		@Nullable
		final Throwable error;

		ResponseWrapper(RequestWrapper request, Response response, @Nullable Throwable error) {
			this.request = request;
			this.response = response;
			this.error = error;
		}

		@Override
		public Object unwrap() {
			return response;
		}

		@Override
		public RequestWrapper request() {
			return request;
		}

		@Override
		@Nullable
		public Throwable error() {
			return error;
		}

		@Override
		public int statusCode() {
			return response.status();
		}

	}

}
