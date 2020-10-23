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

import java.net.InetSocketAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;
import reactor.util.context.Context;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.internal.LazyBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.Nullable;

class HttpClientBeanPostProcessor implements BeanPostProcessor {

	final ConfigurableApplicationContext springContext;

	HttpClientBeanPostProcessor(ConfigurableApplicationContext springContext) {
		this.springContext = springContext;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		LazyBean<CurrentTraceContext> currentContext = LazyBean.create(this.springContext, CurrentTraceContext.class);
		if (bean instanceof HttpClient) {
			// This adds handlers to manage the span lifecycle. All require explicit
			// propagation of the current span as a reactor context property.
			// This done in mapConnect, added last so that it is setup first.
			// https://projectreactor.io/docs/core/release/reference/#_simple_context_examples

			// In our case, we treat a normal response no differently than one in
			// preparation of a redirect follow-up.
			TracingDoOnResponse doOnResponse = new TracingDoOnResponse(springContext);
			return ((HttpClient) bean).doOnResponseError(new TracingDoOnErrorResponse(springContext))
					.doOnRedirect(doOnResponse).doOnResponse(doOnResponse)
					.doOnRequestError(new TracingDoOnErrorRequest(springContext))
					.doOnRequest(new TracingDoOnRequest(springContext)).mapConnect(new TracingMapConnect(() -> {
						CurrentTraceContext ref = currentContext.get();
						return ref != null ? ref.get() : null;
					}));
		}
		return bean;
	}

	/** The current client span, cleared on completion for any reason. */
	static final class PendingSpan extends AtomicReference<Span> {

	}

	static class TracingMapConnect implements Function<Mono<? extends Connection>, Mono<? extends Connection>> {

		static final Exception CANCELLED_ERROR = new CancellationException("CANCELLED") {
			@Override
			public Throwable fillInStackTrace() {
				return this; // stack trace doesn't add value here
			}
		};

		final Supplier<TraceContext> currentTraceContext;

		TracingMapConnect(Supplier<TraceContext> currentTraceContext) {
			this.currentTraceContext = currentTraceContext;
		}

		@Override
		public Mono<? extends Connection> apply(Mono<? extends Connection> mono) {
			// This function is invoked once per-request. We keep a reference to the
			// pending client span here, so that only one signal completes the span.
			PendingSpan pendingSpan = new PendingSpan();
			return mono.contextWrite(context -> {
				TraceContext invocationContext = currentTraceContext.get();
				if (invocationContext != null) {
					// Read in this processor and also in ScopePassingSpanSubscriber
					context = context.put(TraceContext.class, invocationContext);
				}
				return context.put(PendingSpan.class, pendingSpan);
			}).doOnCancel(() -> {
				// Check to see if Subscription.cancel() happened before another signal,
				// like onComplete() completed the span (clearing the reference).
				Span span = pendingSpan.getAndSet(null);
				if (span != null) {
					span.error(CANCELLED_ERROR);
					span.end();
				}
			});
		}

	}

	private static class TracingDoOnRequest implements BiConsumer<HttpClientRequest, Connection> {

		final ConfigurableApplicationContext context;

		HttpClientHandler handler;

		TracingDoOnRequest(ConfigurableApplicationContext context) {
			this.context = context;
		}

		HttpClientHandler handler() {
			if (this.handler == null) {
				this.handler = context.getBean(HttpClientHandler.class);
			}
			return this.handler;
		}

		@Override
		public void accept(HttpClientRequest req, Connection connection) {
			PendingSpan pendingSpan = req.currentContextView().getOrDefault(PendingSpan.class, null);
			if (pendingSpan == null) {
				return; // Somehow TracingMapConnect was not invoked.. skip out
			}

			// All completion hooks clear this reference. If somehow this has a span upon
			// re-entry, the state model in reactor-netty has changed and we need to
			// update this code!
			Span span = pendingSpan.getAndSet(null);
			if (span != null) {
				assert false : "span exists when it shouldn't!";
				span.abandon(); // abandon instead of break
			}

			// Start a new client span with the appropriate parent
			TraceContext parent = req.currentContextView().getOrDefault(TraceContext.class, null);
			HttpClientRequestWrapper request = new HttpClientRequestWrapper(req, connection);

			span = handler().handleSend(request, parent);
			pendingSpan.set(span);
		}

	}

	private static class TracingDoOnResponse extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientResponse, Connection> {

		TracingDoOnResponse(ConfigurableApplicationContext context) {
			super(context);
		}

		@Override
		public void accept(HttpClientResponse response, Connection connection) {
			handle(response.currentContext(), response, null);
		}

	}

	private static class TracingDoOnErrorRequest extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientRequest, Throwable> {

		TracingDoOnErrorRequest(ConfigurableApplicationContext context) {
			super(context);
		}

		@Override
		public void accept(HttpClientRequest req, Throwable error) {
			handle(req.currentContext(), null, error);
		}

	}

	private static class TracingDoOnErrorResponse extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientResponse, Throwable> {

		TracingDoOnErrorResponse(ConfigurableApplicationContext context) {
			super(context);
		}

		@Override
		public void accept(HttpClientResponse response, Throwable error) {
			handle(response.currentContext(), response, error);
		}

	}

	private static abstract class AbstractTracingDoOnHandler {

		final ConfigurableApplicationContext context;

		HttpClientHandler handler;

		AbstractTracingDoOnHandler(ConfigurableApplicationContext context) {
			this.context = context;
		}

		HttpClientHandler handler() {
			if (this.handler == null) {
				this.handler = this.context.getBean(HttpClientHandler.class);
			}
			return this.handler;
		}

		void handle(Context context, @Nullable HttpClientResponse resp, @Nullable Throwable error) {
			PendingSpan pendingSpan = context.getOrDefault(PendingSpan.class, null);
			if (pendingSpan == null) {
				return; // Somehow TracingMapConnect was not invoked.. skip out
			}

			Span span = pendingSpan.getAndSet(null);
			if (span == null) {
				return; // Unexpected. In the handle method, without a span to finish!
			}
			HttpClientResponseWrapper response = new HttpClientResponseWrapper(resp, error);
			handler().handleReceive(response, span);
		}

	}

	static final class HttpClientRequestWrapper implements org.springframework.cloud.sleuth.api.http.HttpClientRequest {

		final HttpClientRequest delegate;

		final Connection connection;

		Boolean inetSocketAddress;

		InetSocketAddress address;

		HttpClientRequestWrapper(HttpClientRequest delegate, Connection connection) {
			this.delegate = delegate;
			this.connection = connection;
		}

		InetSocketAddress address() {
			this.inetSocketAddress = this.inetSocketAddress != null ? this.inetSocketAddress
					: connection.address() instanceof InetSocketAddress;
			if (this.address != null && this.inetSocketAddress) {
				return this.address;
			}
			else if (this.address == null && this.inetSocketAddress) {
				this.address = (InetSocketAddress) connection.address();
				this.inetSocketAddress = true;
				return this.address;
			}
			return null;
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
			return delegate.fullPath();
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

		@Override
		public String remoteIp() {
			InetSocketAddress address = address();
			return address != null ? address.getHostString() : null;
		}

		@Override
		public int remotePort() {
			InetSocketAddress address = address();
			return address != null ? address.getPort() : 0;
		}

	}

	static final class HttpClientResponseWrapper
			implements org.springframework.cloud.sleuth.api.http.HttpClientResponse {

		@Nullable
		final HttpClientResponse delegate;

		HttpClientRequestWrapper request;

		final Throwable error;

		HttpClientResponseWrapper(@Nullable HttpClientResponse delegate, Throwable error) {
			this.delegate = delegate;
			this.error = error;
		}

		@Override
		public Object unwrap() {
			return this.delegate;
		}

		@Override
		public HttpClientRequestWrapper request() {
			if (request == null) {
				if (delegate instanceof HttpClientRequest) {
					this.request = new HttpClientRequestWrapper((HttpClientRequest) delegate, null);
				}
			}
			return this.request;
		}

		@Override
		public int statusCode() {
			if (this.delegate == null) {
				return 0;
			}
			return this.delegate.status().code();
		}

		@Override
		public Throwable error() {
			return this.error;
		}

		@Override
		public String header(String header) {
			if (this.delegate == null) {
				return null;
			}
			return this.delegate.responseHeaders().get(header);
		}

	}

}
