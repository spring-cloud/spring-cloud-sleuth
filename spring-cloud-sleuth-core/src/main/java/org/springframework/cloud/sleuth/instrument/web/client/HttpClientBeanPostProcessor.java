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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import io.netty.bootstrap.Bootstrap;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.internal.LazyBean;
import org.springframework.context.ConfigurableApplicationContext;

class HttpClientBeanPostProcessor implements BeanPostProcessor {

	final ConfigurableApplicationContext springContext;

	HttpClientBeanPostProcessor(ConfigurableApplicationContext springContext) {
		this.springContext = springContext;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		LazyBean<HttpTracing> httpTracing = LazyBean.create(this.springContext,
				HttpTracing.class);
		if (bean instanceof HttpClient) {
			return ((HttpClient) bean).mapConnect(new TracingMapConnect(httpTracing))
					.doOnRequest(TracingDoOnRequest.create(httpTracing))
					.doOnRequestError(TracingDoOnErrorRequest.create(httpTracing))
					.doOnResponse(TracingDoOnResponse.create(httpTracing))
					.doOnResponseError(TracingDoOnErrorResponse.create(httpTracing));
		}
		return bean;
	}

	private static class TracingMapConnect implements
			BiFunction<Mono<? extends Connection>, Bootstrap, Mono<? extends Connection>> {

		final LazyBean<HttpTracing> httpTracing;

		Tracer tracer;

		TracingMapConnect(LazyBean<HttpTracing> httpTracing) {
			this.httpTracing = httpTracing;
		}

		@Override
		public Mono<? extends Connection> apply(Mono<? extends Connection> mono,
				Bootstrap bootstrap) {
			return mono.subscriberContext(context -> context.put(AtomicReference.class,
					new AtomicReference<>(tracer().currentSpan())));
		}

		private Tracer tracer() {
			if (this.tracer == null) {
				this.tracer = this.httpTracing.get().tracing().tracer();
			}
			return this.tracer;
		}

	}

	private static class TracingDoOnRequest
			implements BiConsumer<HttpClientRequest, Connection> {

		final LazyBean<HttpTracing> httpTracing;

		List<String> propagationKeys;

		HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler;

		TracingDoOnRequest(LazyBean<HttpTracing> httpTracing) {
			this.httpTracing = httpTracing;
		}

		static TracingDoOnRequest create(LazyBean<HttpTracing> httpTracing) {
			return new TracingDoOnRequest(httpTracing);
		}

		List<String> propagationKeys() {
			if (this.propagationKeys == null) {
				this.propagationKeys = httpTracing.get().tracing().propagation().keys();
			}
			return this.propagationKeys;
		}

		HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler() {
			if (this.handler == null) {
				this.handler = HttpClientHandler.create(httpTracing.get());
			}
			return this.handler;
		}

		@Override
		public void accept(HttpClientRequest req, Connection connection) {
			// request already instrumented
			// TODO: consider another, cheaper way, like flagging a context
			// property. If not, comment why.
			for (String key : propagationKeys()) {
				if (req.requestHeaders().contains(key)) {
					return;
				}
			}
			AtomicReference<Span> reference = req.currentContext()
					.getOrDefault(AtomicReference.class, new AtomicReference<>());
			WrappedHttpClientRequest request = new WrappedHttpClientRequest(req);
			Span span = reference.get() == null ? handler().handleSend(request)
					: handler().handleSend(request, reference.get());
			reference.set(span);
		}

	}

	private static class TracingDoOnResponse extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientResponse, Connection> {

		TracingDoOnResponse(LazyBean<HttpTracing> httpTracing) {
			super(httpTracing);
		}

		static TracingDoOnResponse create(LazyBean<HttpTracing> httpTracing) {
			return new TracingDoOnResponse(httpTracing);
		}

		@Override
		public void accept(HttpClientResponse httpClientResponse, Connection connection) {
			handle(httpClientResponse, null);
		}

	}

	private static class TracingDoOnErrorRequest extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientRequest, Throwable> {

		TracingDoOnErrorRequest(LazyBean<HttpTracing> httpTracing) {
			super(httpTracing);
		}

		static TracingDoOnErrorRequest create(LazyBean<HttpTracing> httpTracing) {
			return new TracingDoOnErrorRequest(httpTracing);
		}

		@Override
		public void accept(HttpClientRequest request, Throwable throwable) {
			handle(null, throwable);
		}

	}

	private static class TracingDoOnErrorResponse extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientResponse, Throwable> {

		TracingDoOnErrorResponse(LazyBean<HttpTracing> httpTracing) {
			super(httpTracing);
		}

		static TracingDoOnErrorResponse create(LazyBean<HttpTracing> httpTracing) {
			return new TracingDoOnErrorResponse(httpTracing);
		}

		@Override
		public void accept(HttpClientResponse httpClientResponse, Throwable throwable) {
			handle(httpClientResponse, throwable);
		}

	}

	private static abstract class AbstractTracingDoOnHandler {

		final LazyBean<HttpTracing> httpTracing;

		HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler;

		AbstractTracingDoOnHandler(LazyBean<HttpTracing> httpTracing) {
			this.httpTracing = httpTracing;
		}

		HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler() {
			if (this.handler == null) {
				this.handler = HttpClientHandler.create(httpTracing.get());
			}
			return this.handler;
		}

		protected void handle(HttpClientResponse httpClientResponse,
				Throwable throwable) {
			if (httpClientResponse == null) {
				return;
			}
			AtomicReference reference = httpClientResponse.currentContext()
					.getOrDefault(AtomicReference.class, null);
			if (reference == null || reference.get() == null) {
				return;
			}
			handler().handleReceive(new WrappedHttpClientResponse(httpClientResponse),
					throwable, (Span) reference.get());
		}

	}

	static final class WrappedHttpClientRequest extends brave.http.HttpClientRequest {

		final HttpClientRequest delegate;

		WrappedHttpClientRequest(HttpClientRequest delegate) {
			this.delegate = delegate;
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public String method() {
			return delegate.method().name();
		}

		@Override
		public String path() {
			return delegate.path();
		}

		@Override
		public String url() {
			return delegate.uri();
		}

		@Override
		public String header(String name) {
			return delegate.requestHeaders().get(name);
		}

		@Override
		public void header(String name, String value) {
			delegate.header(name, value);
		}

	}

	static final class WrappedHttpClientResponse extends brave.http.HttpClientResponse {

		final HttpClientResponse delegate;

		WrappedHttpClientResponse(HttpClientResponse delegate) {
			this.delegate = delegate;
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public int statusCode() {
			return delegate.status().code();
		}

	}

}
