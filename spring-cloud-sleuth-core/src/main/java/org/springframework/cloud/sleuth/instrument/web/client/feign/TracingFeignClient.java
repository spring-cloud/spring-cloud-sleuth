/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Feign client wrapper
 *
 * @author Marcin Grzejsczak
 * @since 2.0.0
 */
final class TracingFeignClient implements Client {

	private static final Log log = LogFactory.getLog(TracingFeignClient.class);

	static final Propagation.Setter<Map<String, Collection<String>>, String> SETTER =
			new Propagation.Setter<Map<String, Collection<String>>, String>() {
		@Override public void put(Map<String, Collection<String>> carrier, String key,
				String value) {
			if (!carrier.containsKey(key)) {
				carrier.put(key, Collections.singletonList(value));
				if (log.isTraceEnabled()) {
					log.trace("Added key [" + key + "] and header value [" + value + "]");
				}
			} else {
				if (log.isTraceEnabled()) {
					log.trace("Key [" + key + "] already there in the headers");
				}
			}
		}

		@Override public String toString() {
			return "Map::set";
		}
	};

	static Client create(HttpTracing httpTracing, Client delegate) {
		return new TracingFeignClient(httpTracing, delegate);
	}

	final Tracer tracer;
	final Client delegate;
	final HttpClientHandler<Request, Response> handler;
	final TraceContext.Injector<Map<String, Collection<String>>> injector;

	TracingFeignClient(HttpTracing httpTracing, Client delegate) {
		this.tracer = httpTracing.tracing().tracer();
		this.handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
		this.injector = httpTracing.tracing().propagation().injector(SETTER);
		this.delegate = delegate;
	}

	@Override public Response execute(Request request, Request.Options options)
			throws IOException {
		Map<String, Collection<String>> headers = new HashMap<>(request.headers());
		Span span = handleSend(headers, request, null);
		if (log.isDebugEnabled()) {
			log.debug("Handled send of " + span);
		}
		Response response = null;
		Throwable error = null;
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			return response = this.delegate.execute(modifiedRequest(request, headers), options);
		}
		catch (IOException | RuntimeException | Error e) {
			error = e;
			throw e;
		}
		finally {
			handleReceive(span, response, error);
			if (log.isDebugEnabled()) {
				log.debug("Handled receive of " + span);
			}
		}
	}

	Span handleSend(Map<String, Collection<String>> headers, Request request, Span clientSpan) {
		if (clientSpan != null) {
			return this.handler.handleSend(this.injector, headers, request, clientSpan);
		}
		return this.handler.handleSend(this.injector, headers, request);
	}

	void handleReceive(Span span, Response response, Throwable error) {
		this.handler.handleReceive(response, error, span);
	}

	private Request modifiedRequest(Request request, Map<String, Collection<String>> headers) {
		String method = request.method();
		String url = request.url();
		byte[] body = request.body();
		Charset charset = request.charset();
		return Request.create(method, url, headers, body, charset);
	}

	static final class HttpAdapter
			extends brave.http.HttpClientAdapter<Request, Response> {

		@Override public String method(Request request) {
			return request.method();
		}

		@Override public String url(Request request) {
			return request.url();
		}

		@Override public String requestHeader(Request request, String name) {
			Collection<String> result = request.headers().get(name);
			return result != null && result.iterator().hasNext() ?
					result.iterator().next() :
					null;
		}

		@Override public Integer statusCode(Response response) {
			return response.status();
		}
	}
}