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

package org.springframework.cloud.sleuth.brave.instrument.web.client;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import brave.Span;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;
import reactor.util.context.Context;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.internal.LazyBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth.scopePassingSpanOperator;

/**
 * {@link BeanPostProcessor} to wrap a {@link WebClient} instance into its trace
 * representation.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
final class TraceWebClientBeanPostProcessor implements BeanPostProcessor {

	final ConfigurableApplicationContext springContext;

	TraceWebClientBeanPostProcessor(ConfigurableApplicationContext springContext) {
		this.springContext = springContext;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof WebClient) {
			WebClient webClient = (WebClient) bean;
			return wrapBuilder(webClient.mutate()).build();
		}
		else if (bean instanceof WebClient.Builder) {
			WebClient.Builder webClientBuilder = (WebClient.Builder) bean;
			return wrapBuilder(webClientBuilder);
		}
		return bean;
	}

	private WebClient.Builder wrapBuilder(WebClient.Builder webClientBuilder) {
		return webClientBuilder.filters(addTraceExchangeFilterFunctionIfNotPresent());
	}

	private Consumer<List<ExchangeFilterFunction>> addTraceExchangeFilterFunctionIfNotPresent() {
		return functions -> {
			boolean noneMatch = noneMatchTraceExchangeFunction(functions);
			if (noneMatch) {
				functions.add(new TraceExchangeFilterFunction(this.springContext));
			}
		};
	}

	private boolean noneMatchTraceExchangeFunction(List<ExchangeFilterFunction> functions) {
		for (ExchangeFilterFunction function : functions) {
			if (function instanceof TraceExchangeFilterFunction) {
				return false;
			}
		}
		return true;
	}

}

final class TraceExchangeFilterFunction implements ExchangeFilterFunction {

	private static final Log log = LogFactory.getLog(TraceExchangeFilterFunction.class);

	final LazyBean<HttpTracing> httpTracing;

	final Function<? super Publisher<DataBuffer>, ? extends Publisher<DataBuffer>> scopePassingTransformer;

	// Lazy initialized fields
	HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

	CurrentTraceContext currentTraceContext;

	TraceExchangeFilterFunction(ConfigurableApplicationContext springContext) {
		this.httpTracing = LazyBean.create(springContext, HttpTracing.class);
		this.scopePassingTransformer = scopePassingSpanOperator(springContext);
	}

	public static ExchangeFilterFunction create(ConfigurableApplicationContext springContext) {
		return new TraceExchangeFilterFunction(springContext);
	}

	@Override
	public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
		return new MonoWebClientTrace(next, request, this);
	}

	CurrentTraceContext currentTraceContext() {
		if (this.currentTraceContext == null) {
			this.currentTraceContext = httpTracing.get().tracing().currentTraceContext();
		}
		return this.currentTraceContext;
	}

	HttpClientHandler<HttpClientRequest, HttpClientResponse> handler() {
		if (this.handler == null) {
			this.handler = HttpClientHandler.create(this.httpTracing.get());
		}
		return this.handler;
	}

	private static final class MonoWebClientTrace extends Mono<ClientResponse> {

		final ExchangeFunction next;

		final ClientRequest request;

		final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

		final CurrentTraceContext currentTraceContext;

		MonoWebClientTrace(ExchangeFunction next, ClientRequest request, TraceExchangeFilterFunction filterFunction) {
			this.next = next;
			this.request = request;
			this.handler = filterFunction.handler();
			this.currentTraceContext = filterFunction.currentTraceContext();
		}

		@Override
		public void subscribe(CoreSubscriber<? super ClientResponse> subscriber) {
			Context context = subscriber.currentContext();
			if (log.isTraceEnabled()) {
				log.trace("Got the following context [" + context + "]");
			}
			ClientRequestWrapper wrapper = new ClientRequestWrapper(request);
			TraceContext parent = context.hasKey(TraceContext.class) ? context.get(TraceContext.class) : null;
			Span span = handler.handleSendWithParent(wrapper, parent);
			if (log.isDebugEnabled()) {
				log.debug("HttpClientHandler::handleSend: " + span);
			}
			// NOTE: We are starting the client span for the request here, but it could be
			// canceled prior to actually being invoked. TraceWebClientSubscription will
			// abandon this span, if cancel() happens before request().
			this.next.exchange(wrapper.buildRequest())
					.subscribe(new TraceWebClientSubscriber(subscriber, context, span, parent, this));
		}

	}

	static final class TraceWebClientSubscriber extends AtomicReference<Span>
			implements CoreSubscriber<ClientResponse> {

		final CoreSubscriber<? super ClientResponse> actual;

		final Context context;

		@Nullable
		final TraceContext parent;

		final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

		final CurrentTraceContext currentTraceContext;

		TraceWebClientSubscriber(CoreSubscriber<? super ClientResponse> actual, Context ctx, Span clientSpan,
				TraceContext parent, MonoWebClientTrace mono) {
			this.actual = actual;
			this.parent = parent;
			this.handler = mono.handler;
			this.currentTraceContext = mono.currentTraceContext;
			this.context = this.parent != null && !this.parent.equals(ctx.getOrDefault(TraceContext.class, null))
					? ctx.put(TraceContext.class, this.parent) : ctx;
			set(clientSpan);
		}

		@Override
		public void onSubscribe(Subscription subscription) {
			this.actual.onSubscribe(new TraceWebClientSubscription(subscription, this));
		}

		@Override
		public void onNext(ClientResponse response) {
			try (Scope scope = currentTraceContext.maybeScope(parent)) {
				// decorate response body
				this.actual.onNext(response);
			}
			finally {
				Span span = getAndSet(null);
				if (span != null) {
					// TODO: is there a way to read the request at response time?
					this.handler.handleReceive(new ClientResponseWrapper(response), null, span);
				}
			}
		}

		@Override
		public void onError(Throwable t) {
			try (Scope scope = currentTraceContext.maybeScope(parent)) {
				this.actual.onError(t);
			}
			finally {
				Span span = getAndSet(null);
				if (span != null) {
					span.error(t);
					span.finish();
				}
			}
		}

		@Override
		public void onComplete() {
			try (Scope scope = currentTraceContext.maybeScope(parent)) {
				this.actual.onComplete();
			}
			finally {
				Span span = getAndSet(null);
				if (span != null) {
					// TODO: backfill empty test:
					// https://github.com/spring-cloud/spring-cloud-sleuth/issues/1570
					if (log.isDebugEnabled()) {
						log.debug("Reached OnComplete without finishing [" + span + "]");
					}
					span.abandon();
				}
			}
		}

		@Override
		public Context currentContext() {
			return this.context;
		}

	}

	static class TraceWebClientSubscription implements Subscription {

		static final Exception CANCELLED_ERROR = new CancellationException("CANCELLED") {
			@Override
			public Throwable fillInStackTrace() {
				return this; // stack trace doesn't add value here
			}
		};

		final AtomicReference<Span> pendingSpan;

		final Subscription delegate;

		volatile boolean requested;

		TraceWebClientSubscription(Subscription delegate, AtomicReference<Span> pendingSpan) {
			this.delegate = delegate;
			this.pendingSpan = pendingSpan;
		}

		@Override
		public void request(long n) {
			requested = true;
			delegate.request(n); // Not scoping to save overhead
		}

		@Override
		public void cancel() {
			delegate.cancel(); // Not scoping to save overhead

			// Check to see if Subscription.cancel() happened after request(),
			// but before another signal (like onComplete) completed the span.
			Span span = pendingSpan.getAndSet(null);
			if (span != null) {
				if (log.isDebugEnabled()) {
					log.debug("Subscription was cancelled. TraceWebClientBeanPostProcessor Will close the span [" + span
							+ "]");
				}

				if (!requested) { // Abandon the span.
					span.abandon();
				}
				else { // Request was canceled in-flight
					span.error(CANCELLED_ERROR);
					span.finish();
				}
			}
		}

	}

	private static final class ClientRequestWrapper extends HttpClientRequest {

		final ClientRequest delegate;

		final ClientRequest.Builder builder;

		ClientRequestWrapper(ClientRequest delegate) {
			this.delegate = delegate;
			this.builder = ClientRequest.from(delegate);
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
			return delegate.url().getPath();
		}

		@Override
		public String url() {
			return delegate.url().toString();
		}

		@Override
		public String header(String name) {
			return delegate.headers().getFirst(name);
		}

		@Override
		public void header(String name, String value) {
			builder.header(name, value);
		}

		ClientRequest buildRequest() {
			return builder.build();
		}

	}

	static final class ClientResponseWrapper extends HttpClientResponse {

		final ClientResponse delegate;

		ClientResponseWrapper(ClientResponse delegate) {
			this.delegate = delegate;
		}

		@Override
		public Object unwrap() {
			return delegate;
		}

		@Override
		public int statusCode() {
			// unlike statusCode(), this doesn't throw
			return Math.max(delegate.rawStatusCode(), 0);
		}

	}

}
