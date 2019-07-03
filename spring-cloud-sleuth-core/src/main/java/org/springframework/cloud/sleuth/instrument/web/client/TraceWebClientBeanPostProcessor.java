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

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;
import reactor.util.context.Context;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link BeanPostProcessor} to wrap a {@link WebClient} instance into its trace
 * representation.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
final class TraceWebClientBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	TraceWebClientBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof WebClient) {
			WebClient webClient = (WebClient) bean;
			return webClient.mutate()
					.filters(addTraceExchangeFilterFunctionIfNotPresent()).build();
		}
		else if (bean instanceof WebClient.Builder) {
			WebClient.Builder webClientBuilder = (WebClient.Builder) bean;
			return webClientBuilder.filters(addTraceExchangeFilterFunctionIfNotPresent());
		}
		return bean;
	}

	private Consumer<List<ExchangeFilterFunction>> addTraceExchangeFilterFunctionIfNotPresent() {
		return functions -> {
			if (functions.stream()
					.noneMatch(f -> f instanceof TraceExchangeFilterFunction)) {
				functions.add(new TraceExchangeFilterFunction(this.beanFactory));
			}
		};
	}

}

final class TraceExchangeFilterFunction implements ExchangeFilterFunction {

	private static final Log log = LogFactory.getLog(TraceExchangeFilterFunction.class);
	static final Propagation.Setter<ClientRequest.Builder, String> SETTER = new Propagation.Setter<ClientRequest.Builder, String>() {
		@Override
		public void put(ClientRequest.Builder carrier, String key, String value) {
			carrier.headers(httpHeaders -> {
				if (log.isTraceEnabled()) {
					log.trace("Replacing [" + key + "] with value [" + value + "]");
				}
				httpHeaders.merge(key, Collections.singletonList(value),
						(oldValue, newValue) -> newValue);
			});
		}

		@Override
		public String toString() {
			return "ClientRequest.Builder::header";
		}
	};

	private static final String CLIENT_SPAN_KEY = "sleuth.webclient.clientSpan";

	private static final String CANCELLED_SUBSCRIPTION_ERROR = "CANCELLED";

	final BeanFactory beanFactory;

	final Function<? super Publisher<DataBuffer>, ? extends Publisher<DataBuffer>> scopePassingTransformer;

	Tracer tracer;

	HttpTracing httpTracing;

	HttpClientHandler<ClientRequest, ClientResponse> handler;

	TraceContext.Injector<ClientRequest.Builder> injector;

	TraceExchangeFilterFunction(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.scopePassingTransformer = ReactorSleuth
				.scopePassingSpanOperator(beanFactory);
	}

	public static ExchangeFilterFunction create(BeanFactory beanFactory) {
		return new TraceExchangeFilterFunction(beanFactory);
	}

	@Override
	public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
		ClientRequest.Builder builder = ClientRequest.from(request);
		if (log.isDebugEnabled()) {
			log.debug("Instrumenting WebClient call");
		}
		Span span = handler().handleSend(injector(), builder, request,
				tracer().nextSpan());
		if (log.isDebugEnabled()) {
			log.debug("Handled send of " + span);
		}

		return new MonoWebClientTrace(next, builder.build(), this, span);
	}

	@SuppressWarnings("unchecked")
	HttpClientHandler<ClientRequest, ClientResponse> handler() {
		if (this.handler == null) {
			this.handler = HttpClientHandler.create(
					this.beanFactory.getBean(HttpTracing.class),
					new TraceExchangeFilterFunction.HttpAdapter());
		}
		return this.handler;
	}

	Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = httpTracing().tracing().tracer();
		}
		return this.tracer;
	}

	HttpTracing httpTracing() {
		if (this.httpTracing == null) {
			this.httpTracing = this.beanFactory.getBean(HttpTracing.class);
		}
		return this.httpTracing;
	}

	TraceContext.Injector<ClientRequest.Builder> injector() {
		if (this.injector == null) {
			this.injector = this.beanFactory.getBean(HttpTracing.class).tracing()
					.propagation().injector(SETTER);
		}
		return this.injector;
	}

	private static final class MonoWebClientTrace extends Mono<ClientResponse> {

		final ExchangeFunction next;

		final ClientRequest request;

		final Tracer tracer;

		final HttpClientHandler<ClientRequest, ClientResponse> handler;

		final TraceContext.Injector<ClientRequest.Builder> injector;

		final Tracing tracing;

		final Function<? super Publisher<DataBuffer>, ? extends Publisher<DataBuffer>> scopePassingTransformer;

		private final Span span;

		MonoWebClientTrace(ExchangeFunction next, ClientRequest request,
				TraceExchangeFilterFunction parent, Span span) {
			this.next = next;
			this.request = request;
			this.tracer = parent.tracer();
			this.handler = parent.handler();
			this.injector = parent.injector();
			this.tracing = parent.httpTracing().tracing();
			this.scopePassingTransformer = parent.scopePassingTransformer;
			this.span = span;
		}

		@Override
		public void subscribe(CoreSubscriber<? super ClientResponse> subscriber) {

			Context context = subscriber.currentContext();

			this.next.exchange(request).subscribe(
					new WebClientTracerSubscriber(subscriber, context, span, this));
		}

		static final class WebClientTracerSubscriber
				implements CoreSubscriber<ClientResponse> {

			final CoreSubscriber<? super ClientResponse> actual;

			final Context context;

			final Span span;

			final Tracer.SpanInScope ws;

			final HttpClientHandler<ClientRequest, ClientResponse> handler;

			final Function<? super Publisher<DataBuffer>, ? extends Publisher<DataBuffer>> scopePassingTransformer;

			final Tracing tracing;

			boolean done;

			WebClientTracerSubscriber(CoreSubscriber<? super ClientResponse> actual,
					Context context, Span span, MonoWebClientTrace parent) {
				this.actual = actual;
				this.span = span;
				this.handler = parent.handler;
				this.tracing = parent.tracing;
				this.scopePassingTransformer = parent.scopePassingTransformer;

				if (!context.hasKey(Span.class)) {
					context = context.put(Span.class, span);
					if (log.isDebugEnabled()) {
						log.debug("Reactor Context got injected with the client span "
								+ span);
					}
				}

				this.context = context.put(CLIENT_SPAN_KEY, span);
				this.ws = parent.tracer.withSpanInScope(span);

			}

			@Override
			public void onSubscribe(Subscription subscription) {
				this.actual.onSubscribe(new Subscription() {
					@Override
					public void request(long n) {
						subscription.request(n);
					}

					@Override
					public void cancel() {
						terminateSpanOnCancel();
						subscription.cancel();
					}
				});
			}

			@Override
			public void onNext(ClientResponse response) {
				this.done = true;
				try {
					// decorate response body
					this.actual
							.onNext(ClientResponse.from(response)
									.body(response.bodyToFlux(DataBuffer.class)
											.transform(this.scopePassingTransformer))
									.build());
				}
				finally {
					terminateSpan(response, null);
				}
			}

			@Override
			public void onError(Throwable t) {
				try {
					this.actual.onError(t);
				}
				finally {
					terminateSpan(null, t);
				}
			}

			@Override
			public void onComplete() {
				try {
					this.actual.onComplete();
				}
				finally {
					if (!this.done) {
						terminateSpan(null, null);
					}
				}
			}

			@Override
			public Context currentContext() {
				return this.context;
			}

			void handleReceive(Span clientSpan, Tracer.SpanInScope ws,
					ClientResponse clientResponse, Throwable throwable) {
				this.handler.handleReceive(clientResponse, throwable, clientSpan);
				ws.close();
			}

			void terminateSpanOnCancel() {
				if (log.isDebugEnabled()) {
					log.debug("Subscription was cancelled. Will close the span ["
							+ this.span + "]");
				}

				this.span.tag("error", CANCELLED_SUBSCRIPTION_ERROR);
				handleReceive(this.span, this.ws, null, null);
			}

			void terminateSpan(@Nullable ClientResponse clientResponse,
					@Nullable Throwable throwable) {
				if (clientResponse == null || clientResponse.statusCode() == null) {
					if (log.isDebugEnabled()) {
						log.debug("No response was returned. Will close the span ["
								+ this.span + "]");
					}
					handleReceive(this.span, this.ws, clientResponse, throwable);
					return;
				}
				boolean error = clientResponse.statusCode().is4xxClientError()
						|| clientResponse.statusCode().is5xxServerError();
				if (error) {
					if (log.isDebugEnabled()) {
						log.debug(
								"Non positive status code was returned from the call. Will close the span ["
										+ this.span + "]");
					}
					throwable = new RestClientException("Status code of the response is ["
							+ clientResponse.statusCode().value()
							+ "] and the reason is ["
							+ clientResponse.statusCode().getReasonPhrase() + "]");
				}
				handleReceive(this.span, this.ws, clientResponse, throwable);
			}

		}

	}

	static final class HttpAdapter
			extends brave.http.HttpClientAdapter<ClientRequest, ClientResponse> {

		@Override
		public String method(ClientRequest request) {
			return request.method().name();
		}

		@Override
		public String url(ClientRequest request) {
			return request.url().toString();
		}

		@Override
		public String requestHeader(ClientRequest request, String name) {
			Object result = request.headers().getFirst(name);
			return result != null ? result.toString() : null;
		}

		@Override
		public Integer statusCode(ClientResponse response) {
			return response.rawStatusCode();
		}

		@Override
		public int statusCodeAsInt(ClientResponse response) {
			try {
				return super.statusCodeAsInt(response);
			}
			catch (Exception dontCare) {
				return 0;
			}
		}

	}

}
