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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import brave.Span;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
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
			return ((HttpClient) bean)
					.doOnResponseError(new TracingDoOnErrorResponse(httpTracing))
					.doOnResponse(new TracingDoOnResponse(httpTracing))
					.doOnRequestError(new TracingDoOnErrorRequest(httpTracing))
					.doOnRequest(new TracingDoOnRequest(httpTracing))
					.mapConnect(new TracingMapConnect(httpTracing));
		}
		return bean;
	}

	/** current client span, cleared on completion. */
	private static final class CurrentClientSpan extends AtomicReference<Span> {

	}

	private static class TracingMapConnect implements
			BiFunction<Mono<? extends Connection>, Bootstrap, Mono<? extends Connection>> {

		final LazyBean<HttpTracing> httpTracing;

		CurrentTraceContext currentTraceContext;

		TracingMapConnect(LazyBean<HttpTracing> httpTracing) {
			this.httpTracing = httpTracing;
		}

		@Override
		public Mono<? extends Connection> apply(Mono<? extends Connection> mono,
				Bootstrap bootstrap) {
			return mono.subscriberContext(context -> {
				TraceContext invocationContext = currentTraceContext().get();
				if (invocationContext != null) {
					// Read in this processor and also in ScopePassingSpanSubscriber
					context = context.put(TraceContext.class, invocationContext);
				}
				return context.put(CurrentClientSpan.class, new CurrentClientSpan());
			});
		}

		CurrentTraceContext currentTraceContext() {
			if (this.currentTraceContext == null) {
				this.currentTraceContext = this.httpTracing.get().tracing()
						.currentTraceContext();
			}
			return this.currentTraceContext;
		}

	}

	private static class TracingDoOnRequest
			implements BiConsumer<HttpClientRequest, Connection> {

		final LazyBean<HttpTracing> httpTracing;

		HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler;

		TracingDoOnRequest(LazyBean<HttpTracing> httpTracing) {
			this.httpTracing = httpTracing;
		}

		HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler() {
			if (this.handler == null) {
				this.handler = HttpClientHandler.create(httpTracing.get());
			}
			return this.handler;
		}

		CurrentTraceContext currentTraceContext() {
			return httpTracing.get().tracing().currentTraceContext();
		}

		@Override
		public void accept(HttpClientRequest req, Connection connection) {
			CurrentClientSpan ref = req.currentContext()
					.getOrDefault(CurrentClientSpan.class, null);
			if (ref == null) { // Somehow TracingMapConnect was not invoked.. skip out
				return;
			}

			// This might be re-entrant on auto-redirect or connection retry:
			// See reactor/reactor-netty#1000 for follow-ups.
			Span clientSpan = ref.getAndSet(null);
			if (clientSpan != null) {
				// Retry from a connect fail wouldn't have parsed the request, leading to
				// an empty span with no data if we finished it. An auto-redirect would
				// have parsed the request, but we have no idea which status code it
				// finished with. Since we can't see the preceding request state, we
				// abandon its span in favor of the next.
				clientSpan.abandon();
			}

			// Start a new client span with the appropriate parent
			TraceContext parent = req.currentContext().getOrDefault(TraceContext.class,
					null);
			WrappedHttpClientRequest request = new WrappedHttpClientRequest(req);

			clientSpan = handler().handleSendWithParent(request, parent);
			parseConnectionAddress(connection, clientSpan);
			ref.set(clientSpan);
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
			// TODO: is there a way to read the request at response time?
			handle(response.currentContext(), response, null);
		}

	}

	private static class TracingDoOnErrorRequest extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientRequest, Throwable> {

		TracingDoOnErrorRequest(LazyBean<HttpTracing> httpTracing) {
			super(httpTracing);
		}

		@Override
		public void accept(HttpClientRequest req, Throwable error) {
			handle(req.currentContext(), null, error);
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
			CurrentClientSpan ref = context.getOrDefault(CurrentClientSpan.class, null);
			if (ref == null) { // Somehow TracingMapConnect was not invoked.. skip out
				return;
			}

			Span clientSpan = ref.getAndSet(null);
			if (clientSpan == null) {
				return; // Unexpected. In the handle method, without a span to finish!
			}
			WrappedHttpClientResponse response = resp != null
					? new WrappedHttpClientResponse(resp) : null;
			handler().handleReceive(response, error, clientSpan);
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
