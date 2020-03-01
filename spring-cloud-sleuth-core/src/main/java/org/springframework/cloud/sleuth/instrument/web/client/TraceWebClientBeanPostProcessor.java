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
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;

import brave.Span;
import brave.http.HttpClientHandler;
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

	private boolean noneMatchTraceExchangeFunction(
			List<ExchangeFilterFunction> functions) {
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

	static final Exception CANCELLED_ERROR = new CancellationException("CANCELLED") {
		@Override
		public Throwable fillInStackTrace() {
			return this; // stack trace doesn't add value here
		}
	};

	final LazyBean<HttpTracing> httpTracing;

	final Function<? super Publisher<DataBuffer>, ? extends Publisher<DataBuffer>> scopePassingTransformer;

	// Lazy initialized fields
	HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler;

	CurrentTraceContext currentTraceContext;

	TraceExchangeFilterFunction(ConfigurableApplicationContext springContext) {
		this.httpTracing = LazyBean.create(springContext, HttpTracing.class);
		this.scopePassingTransformer = scopePassingSpanOperator(springContext);
	}

	public static ExchangeFilterFunction create(
			ConfigurableApplicationContext springContext) {
		return new TraceExchangeFilterFunction(springContext);
	}

	@Override
	public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
		return new MonoWebClientTrace(next, request, this, currentTraceContext.get());
	}

	CurrentTraceContext currentTraceContext() {
		if (this.currentTraceContext == null) {
			this.currentTraceContext = httpTracing.get().tracing().currentTraceContext();
		}
		return this.currentTraceContext;
	}

	HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler() {
		if (this.handler == null) {
			this.handler = HttpClientHandler.create(this.httpTracing.get());
		}
		return this.handler;
	}

	private static final class MonoWebClientTrace extends Mono<ClientResponse> {

		final ExchangeFunction next;

		final ClientRequest request;

		final HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler;

		final CurrentTraceContext currentTraceContext;

		final Function<? super Publisher<DataBuffer>, ? extends Publisher<DataBuffer>> scopePassingTransformer;

		@Nullable
		final TraceContext parent;

		MonoWebClientTrace(ExchangeFunction next, ClientRequest request,
				TraceExchangeFilterFunction filterFunction,
				@Nullable TraceContext parent) {
			this.next = next;
			this.request = request;
			this.handler = filterFunction.handler();
			this.currentTraceContext = filterFunction.currentTraceContext();
			this.scopePassingTransformer = filterFunction.scopePassingTransformer;
			this.parent = parent;
		}

		@Override
		public void subscribe(CoreSubscriber<? super ClientResponse> subscriber) {

			Context context = subscriber.currentContext();

			HttpClientRequest wrapper = new HttpClientRequest(request);
			Span span = handler.handleSendWithParent(wrapper, parent);
			if (log.isDebugEnabled()) {
				log.debug("HttpClientHandler::handleSend: " + span);
			}

			this.next.exchange(wrapper.buildRequest()).subscribe(
					new WebClientTracerSubscriber(subscriber, context, span, this));
		}

	}

	private static final class WebClientTracerSubscriber
			implements CoreSubscriber<ClientResponse> {

		final CoreSubscriber<? super ClientResponse> actual;

		final Context context;

		@Nullable
		final TraceContext parent;

		final Span clientSpan;

		final HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler;

		final Function<? super Publisher<DataBuffer>, ? extends Publisher<DataBuffer>> scopePassingTransformer;

		final CurrentTraceContext currentTraceContext;

		// TODO: this isn't implemented correctly. error and success could both be called
		boolean done;

		WebClientTracerSubscriber(CoreSubscriber<? super ClientResponse> actual,
				Context ctx, Span clientSpan, MonoWebClientTrace mono) {
			this.actual = actual;
			this.parent = mono.parent;
			this.clientSpan = clientSpan;
			this.handler = mono.handler;
			this.currentTraceContext = mono.currentTraceContext;
			this.scopePassingTransformer = mono.scopePassingTransformer;
			this.context = parent != null
					&& !parent.equals(ctx.getOrDefault(TraceContext.class, null))
							? ctx.put(TraceContext.class, parent) : ctx;
		}

		@Override
		public void onSubscribe(Subscription subscription) {
			this.actual.onSubscribe(new Subscription() {
				@Override
				public void request(long n) {
					try (Scope scope = currentTraceContext.maybeScope(parent)) {
						subscription.request(n);
					}
				}

				@Override
				public void cancel() {
					try (Scope scope = currentTraceContext.maybeScope(parent)) {
						subscription.cancel();
					}
					finally { // TODO: this is probably incorrect as cancel happens
								// routinely in unary subscription.
						if (log.isDebugEnabled()) {
							log.debug("Subscription was cancelled. Will close the span ["
									+ clientSpan + "]");
						}
						handleReceive(null, CANCELLED_ERROR);
					}
				}
			});
		}

		@Override
		public void onNext(ClientResponse response) {
			try (Scope scope = currentTraceContext.maybeScope(parent)) {
				this.done = true;
				// decorate response body
				this.actual
						.onNext(ClientResponse.from(response)
								.body(response.bodyToFlux(DataBuffer.class)
										.transform(this.scopePassingTransformer))
								.build());
			}
			finally {
				// TODO: is there a way to read the request at response time?
				handleReceive(response, null);
			}
		}

		@Override
		public void onError(Throwable t) {
			try (Scope scope = currentTraceContext.maybeScope(parent)) {
				this.actual.onError(t);
			}
			finally {
				handleReceive(null, t);
			}
		}

		@Override
		public void onComplete() {
			try (Scope scope = currentTraceContext.maybeScope(parent)) {
				this.actual.onComplete();
			}
			finally {
				// TODO: onComplete should be after onNext. Why are we handling this?
				if (!this.done) { // unknown state
					if (log.isDebugEnabled()) {
						log.debug("Reached OnComplete without finishing ["
								+ this.clientSpan + "]");
					}
					this.clientSpan.abandon();
				}
			}
		}

		@Override
		public Context currentContext() {
			return this.context;
		}

		void handleReceive(@Nullable ClientResponse res, @Nullable Throwable error) {
			HttpClientResponse response = res != null ? new HttpClientResponse(res)
					: null;
			this.handler.handleReceive(response, error, clientSpan);
		}

	}

	private static final class HttpClientRequest extends brave.http.HttpClientRequest {

		final ClientRequest delegate;

		final ClientRequest.Builder builder;

		HttpClientRequest(ClientRequest delegate) {
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

	static final class HttpClientResponse extends brave.http.HttpClientResponse {

		final ClientResponse delegate;

		HttpClientResponse(ClientResponse delegate) {
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
