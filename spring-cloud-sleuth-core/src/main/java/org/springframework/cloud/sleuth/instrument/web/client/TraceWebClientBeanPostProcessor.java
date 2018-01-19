package org.springframework.cloud.sleuth.instrument.web.client;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import reactor.core.publisher.Mono;
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
					.filter(new TraceExchangeFilterFunction(this.beanFactory))
					.build();
		} else if (bean instanceof WebClient.Builder) {
			WebClient.Builder webClientBuilder = (WebClient.Builder) bean;
			return webClientBuilder.filter(new TraceExchangeFilterFunction(this.beanFactory));
		}
		return bean;
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

	public static ExchangeFilterFunction create(BeanFactory beanFactory) {
		return new TraceExchangeFilterFunction(beanFactory);
	}

	final BeanFactory beanFactory;
	Tracer tracer;
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
					Throwable throwable = null;
					ClientResponse response = null;
					try (Tracer.SpanInScope ws = tracer().withSpanInScope(clientSpan)) {
						if (any instanceof Throwable) {
							throwable = (Throwable) any;
							continuation = Mono.error(throwable);
						} else {
							response = (ClientResponse) any;
							boolean error = response.statusCode().is4xxClientError() ||
									response.statusCode().is5xxServerError();
							if (error) {
								if (log.isDebugEnabled()) {
									log.debug(
											"Non positive status code was returned from the call. Will close the span ["
													+ clientSpan + "]");
								}
								throwable = new RestClientException(
										"Status code of the response is [" + response.statusCode()
												.value() + "] and the reason is [" + response
												.statusCode().getReasonPhrase() + "]");
							}
							continuation = Mono.just(response);
						}
					} finally {
						handler().handleReceive(response, throwable, clientSpan);
					}
					return continuation;
				})
				.subscriberContext(c -> {
					if (log.isDebugEnabled()) {
						log.debug("Creating a client span for the WebClient");
					}
					Span parent = c.getOrDefault(Span.class, null);
					Span clientSpan = handler().handleSend(injector(), builder, request,
							parent != null ? parent : tracer().nextSpan());
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
			this.tracer = this.beanFactory.getBean(HttpTracing.class).tracing().tracer();
		}
		return this.tracer;
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