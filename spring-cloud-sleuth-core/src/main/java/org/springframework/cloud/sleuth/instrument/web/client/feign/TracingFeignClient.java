/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Feign client wrapper.
 *
 * @author Marcin Grzejsczak
 * @since 2.0.0
 */
final class TracingFeignClient implements Client {

	private static final Log log = LogFactory.getLog(TracingFeignClient.class);

	static final Propagation.Setter<Map<String, Collection<String>>, String> SETTER = new Propagation.Setter<Map<String, Collection<String>>, String>() {
		@Override
		public void put(Map<String, Collection<String>> carrier, String key,
				String value) {
			if (!carrier.containsKey(key)) {
				carrier.put(key, Collections.singletonList(value));
				if (log.isTraceEnabled()) {
					log.trace("Added key [" + key + "] and header value [" + value + "]");
				}
			}
			else {
				if (log.isTraceEnabled()) {
					log.trace("Key [" + key + "] already there in the headers");
				}
			}
		}

		@Override
		public String toString() {
			return "Map::set";
		}
	};

	final Tracer tracer;

	final Client delegate;

	final HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler;

	TracingFeignClient(HttpTracing httpTracing, Client delegate) {
		this.tracer = httpTracing.tracing().tracer();
		this.handler = HttpClientHandler.create(httpTracing);
		this.delegate = delegate;
	}

	static Client create(HttpTracing httpTracing, Client delegate) {
		return new TracingFeignClient(httpTracing, delegate);
	}

	@Override
	public Response execute(Request req, Request.Options options) throws IOException {
		HttpClientRequest request = new HttpClientRequest(req);
		Span span = this.handler.handleSend(request);
		if (log.isDebugEnabled()) {
			log.debug("Handled send of " + span);
		}
		HttpClientResponse response = null;
		Throwable error = null;
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			Response res = this.delegate.execute(request.build(), options);
			if (res != null) { // possibly null on bad implementation or mocks
				response = new HttpClientResponse(res);
			}
			return res;
		}
		catch (IOException | RuntimeException | Error e) {
			error = e;
			throw e;
		}
		finally {
			this.handler.handleReceive(response, error, span);

			if (log.isDebugEnabled()) {
				log.debug("Handled receive of " + span);
			}
		}
	}

	void handleSendAndReceive(Span span, Request request, Response response,
			Throwable error) {
		this.handler.handleSend(new HttpClientRequest(request), span);
		this.handler.handleReceive(
				response != null ? new HttpClientResponse(response) : null, error, span);
	}

	static final class HttpClientRequest extends brave.http.HttpClientRequest {

		final Request delegate;

		Map<String, Collection<String>> headers;

		HttpClientRequest(Request delegate) {
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
			return result != null && result.iterator().hasNext()
					? result.iterator().next() : null;
		}

		@Override
		public void header(String name, String value) {
			if (headers == null) {
				headers = new LinkedHashMap<>(delegate.headers());
			}
			if (!headers.containsKey(name)) {
				headers.put(name, Collections.singletonList(value));
				if (log.isTraceEnabled()) {
					log.trace(
							"Added key [" + name + "] and header value [" + value + "]");
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
			String method = delegate.method();
			String url = delegate.url();
			byte[] body = delegate.body();
			Charset charset = delegate.charset();
			return Request.create(method, url, headers, body, charset);
		}

	}

	static final class HttpClientResponse extends brave.http.HttpClientResponse {

		final Response delegate;

		HttpClientResponse(Response delegate) {
			this.delegate = delegate;
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public int statusCode() {
			return delegate.status();
		}

	}

}
