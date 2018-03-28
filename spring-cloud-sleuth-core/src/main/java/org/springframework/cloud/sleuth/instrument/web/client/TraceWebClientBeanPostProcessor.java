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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.List;
import java.util.function.Consumer;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * {@link BeanPostProcessor} to wrap a {@link WebClient} instance into
 * its trace representation
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
class TraceWebClientBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	TraceWebClientBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	@Override public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof WebClient) {
			WebClient webClient = (WebClient) bean;
			return webClient
					.mutate()
					.filters(addTraceExchangeFilterFunctionIfNotPresent())
					.build();
		} else if (bean instanceof WebClient.Builder) {
			WebClient.Builder webClientBuilder = (WebClient.Builder) bean;
			return webClientBuilder.filters(addTraceExchangeFilterFunctionIfNotPresent());
		}
		return bean;
	}

	private Consumer<List<ExchangeFilterFunction>> addTraceExchangeFilterFunctionIfNotPresent() {
		return functions -> {
			if (functions
					.stream()
					.noneMatch(f -> f instanceof TraceExchangeFilterFunction)) {
				functions.add(new TraceExchangeFilterFunction(this.beanFactory));
			}
		};
	}
}

class TraceExchangeFilterFunction implements ExchangeFilterFunction {

	private static final Log log = LogFactory.getLog(
			TraceExchangeFilterFunction.class);
	private static final String CLIENT_SPAN_KEY = "sleuth.webclient.clientSpan";

	static final Propagation.Setter<ClientRequest.Builder, String> SETTER =
			new Propagation.Setter<ClientRequest.Builder, String>() {
				@Override public void put(ClientRequest.Builder carrier, String key, String value) {
					carrier.header(key, value);
				}

				@Override public String toString() {
					return "ClientRequest.Builder::header";
				}
			};

	static final Propagation.Getter<ClientRequest, String> GETTER = new Propagation.Getter<ClientRequest, String>() {
		@Override public String get(ClientRequest carrier, String key) {
			return carrier.headers().getFirst(key);
		}

		@Override public String toString() {
			return "HttpHeaders::getFirst";
		}
	};

	public static ExchangeFilterFunction create(BeanFactory beanFactory) {
		return new TraceExchangeFilterFunction(beanFactory);
	}

	final BeanFactory beanFactory;
	Tracer tracer;
	HttpTracing httpTracing;
	HttpClientHandler<ClientRequest, ClientResponse> handler;
	TraceContext.Injector<ClientRequest.Builder> injector;

	TraceExchangeFilterFunction(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override public Mono<ClientResponse> filter(ClientRequest request,
			ExchangeFunction next) {
		final ClientRequest.Builder builder = ClientRequest.from(request);
		Mono<ClientResponse> exchange = Mono
				.defer(() -> next.exchange(builder.build()))
				.cast(Object.class)
				.onErrorResume(Mono::just)
				.zipWith(Mono.subscriberContext())
				.flatMap(anyAndContext -> {
					Object any = anyAndContext.getT1();
					Span clientSpan = anyAndContext.getT2().get(CLIENT_SPAN_KEY);
					Mono<ClientResponse> continuation;
					final Tracer.SpanInScope ws = tracer().withSpanInScope(clientSpan);
						if (any instanceof Throwable) {
							continuation = Mono.error((Throwable) any);
						} else {
							continuation = Mono.just((ClientResponse) any);
						}
					return continuation.doAfterSuccessOrError(
							(clientResponse, throwable1) -> {
								Throwable throwable = throwable1;
								if (clientResponse == null || clientResponse.statusCode() == null) {
									if (log.isDebugEnabled()) {
										log.debug(
												"No response was returned. Will close the span ["
														+ clientSpan + "]");
									}
									handleReceive(clientSpan, ws, clientResponse, throwable);
									return;
								}
								boolean error = clientResponse.statusCode().is4xxClientError() ||
										clientResponse.statusCode().is5xxServerError();
								if (error) {
									if (log.isDebugEnabled()) {
										log.debug(
												"Non positive status code was returned from the call. Will close the span ["
														+ clientSpan + "]");
									}
									throwable = new RestClientException(
											"Status code of the response is [" + clientResponse.statusCode()
													.value() + "] and the reason is [" + clientResponse
													.statusCode().getReasonPhrase() + "]");
								}
								handleReceive(clientSpan, ws, clientResponse, throwable);
							});
				})
				.subscriberContext(c -> {
					if (log.isDebugEnabled()) {
						log.debug("Instrumenting WebClient call");
					}
					Span parent = c.getOrDefault(Span.class, null);
					Span clientSpan = handler().handleSend(injector(), builder,
							request, tracer().nextSpan());
					if (log.isDebugEnabled()) {
						log.debug("Created a client span for the WebClient " + clientSpan);
					}
					if (parent == null) {
						c = c.put(Span.class, clientSpan);
						if (log.isDebugEnabled()) {
							log.debug("Reactor Context got injected with the client span " + clientSpan);
						}
					}
					return c.put(CLIENT_SPAN_KEY, clientSpan);
				});
		return exchange;
	}

	private void handleReceive(Span clientSpan, Tracer.SpanInScope ws,
			ClientResponse clientResponse, Throwable throwable) {
		handler().handleReceive(clientResponse, throwable, clientSpan);
		ws.close();
	}

	@SuppressWarnings("unchecked")
	HttpClientHandler<ClientRequest, ClientResponse> handler() {
		if (this.handler == null) {
			this.handler = HttpClientHandler
					.create(this.beanFactory.getBean(HttpTracing.class), new TraceExchangeFilterFunction.HttpAdapter());
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
			this.injector = this.beanFactory.getBean(HttpTracing.class)
					.tracing().propagation().injector(SETTER);
		}
		return this.injector;
	}


	static final class HttpAdapter
			extends brave.http.HttpClientAdapter<ClientRequest, ClientResponse> {

		@Override public String method(ClientRequest request) {
			return request.method().name();
		}

		@Override public String url(ClientRequest request) {
			return request.url().toString();
		}

		@Override public String requestHeader(ClientRequest request, String name) {
			Object result = request.headers().getFirst(name);
			return result != null ? result.toString() : null;
		}

		@Override public Integer statusCode(ClientResponse response) {
			return response.statusCode().value();
		}
	}
}