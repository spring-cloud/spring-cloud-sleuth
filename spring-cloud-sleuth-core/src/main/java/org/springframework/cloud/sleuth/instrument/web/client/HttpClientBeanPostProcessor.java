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

import java.net.InetSocketAddress;
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
import reactor.util.context.Context;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.internal.LazyBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.Nullable;

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
			// This adds handlers to manage the span lifecycle. All require explicit
			// propagation of the current span as a reactor context property.
			// This done in mapConnect, added last so that it is setup first.
			// https://projectreactor.io/docs/core/release/reference/#_simple_context_examples
			return ((HttpClient) bean).doOnRequest(new TracingDoOnRequest(httpTracing))
					.doOnRequestError(new TracingDoOnErrorRequest(httpTracing))
					.doOnResponse(new TracingDoOnResponse(httpTracing))
					.doOnResponseError(new TracingDoOnErrorResponse(httpTracing))
					.mapConnect(new TracingMapConnect(httpTracing));
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
			// This is read in this class and also inside ScopePassingSpanSubscriber
			return mono.subscriberContext(context -> context.put(AtomicReference.class,
					new AtomicReference<>(tracer().currentSpan())));
		}

		Tracer tracer() {
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

			// Look for a parent propagated by TracingMapConnect
			AtomicReference<Span> ref = req.currentContext()
					.getOrDefault(AtomicReference.class, null);
			Span parent = ref != null ? ref.get() : null;

			// Start a new client span with the appropriate parent
			WrappedHttpClientRequest request = new WrappedHttpClientRequest(req);
			Span clientSpan = parent != null ? handler().handleSend(request, parent)
					: handler().handleSend(request);
			parseConnectionAddress(connection, clientSpan);

			// Swap the ref with the client span, so that other hooks can see it
			if (ref != null) {
				ref.set(clientSpan);
			}
		}

		static void parseConnectionAddress(Connection connection, Span span) {
			if (span.isNoop()) {
				return;
			}
			InetSocketAddress socketAddress = connection.address();
			span.remoteIpAndPort(socketAddress.getHostString(), socketAddress.getPort());
		}

	}

	private static class TracingDoOnResponse extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientResponse, Connection> {

		TracingDoOnResponse(LazyBean<HttpTracing> httpTracing) {
			super(httpTracing);
		}

		@Override
		public void accept(HttpClientResponse response, Connection connection) {
			handle(response.currentContext(), response, null);
		}

	}

	private static class TracingDoOnErrorRequest extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientRequest, Throwable> {

		TracingDoOnErrorRequest(LazyBean<HttpTracing> httpTracing) {
			super(httpTracing);
		}

		@Override
		public void accept(HttpClientRequest request, Throwable error) {
			// TODO: the current context here does not have the AtomicReference<Span>
			handle(request.currentContext(), null, error);
		}

	}

	private static class TracingDoOnErrorResponse extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientResponse, Throwable> {

		TracingDoOnErrorResponse(LazyBean<HttpTracing> httpTracing) {
			super(httpTracing);
		}

		@Override
		public void accept(HttpClientResponse response, Throwable error) {
			handle(response.currentContext(), response, error);
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

		void handle(Context context, @Nullable HttpClientResponse resp,
				@Nullable Throwable error) {
			AtomicReference<Span> ref = context.getOrDefault(AtomicReference.class, null);
			Span span = ref != null ? ref.get() : null;
			if (span == null) {
				return; // Unexpected. In the handle method, without a span to finish!
			}
			WrappedHttpClientResponse response = resp != null
					? new WrappedHttpClientResponse(resp) : null;
			handler().handleReceive(response, error, span);
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
			return "/" + delegate.path(); // TODO: reactor/reactor-netty#999
		}

		@Override
		public String url() {
			return delegate.resourceUrl();
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
		public String method() {
			return delegate.method().name();
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
