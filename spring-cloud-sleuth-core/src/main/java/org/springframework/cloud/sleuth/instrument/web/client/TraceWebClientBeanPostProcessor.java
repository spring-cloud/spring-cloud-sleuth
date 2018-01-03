package org.springframework.cloud.sleuth.instrument.web.client;

import java.net.URI;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpSpanInjector;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.util.SpanNameUtil;
import org.springframework.util.StringUtils;
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

	private static final String CLIENT_SPAN_KEY = "sleuth.webclient.clientSpan";

	private static final Log log = LogFactory.getLog(TraceExchangeFilterFunction.class);

	private Tracer tracer;
	private HttpSpanInjector spanInjector;
	private HttpTraceKeysInjector keysInjector;
	private ErrorParser errorParser;
	private final BeanFactory beanFactory;

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

					tracer().continueSpan(clientSpan);

					Mono<ClientResponse> continuation;
					if (any instanceof Throwable) {
						Throwable throwable = (Throwable) any;
						errorParser().parseErrorTags(clientSpan, throwable);
						continuation = Mono.error(throwable);
					} else {
						ClientResponse response = (ClientResponse) any;
						boolean error = response.statusCode().is4xxClientError() || response
								.statusCode().is5xxServerError();
						if (error) {
							if (log.isDebugEnabled()) {
								log.debug(
										"Non positive status code was returned from the call. Will close the span ["
												+ clientSpan + "]");
							}
							errorParser().parseErrorTags(clientSpan, new RestClientException(
									"Status code of the response is [" + response.statusCode()
											.value() + "] and the reason is [" + response
											.statusCode().getReasonPhrase() + "]"));
						}
						continuation = Mono.just(response);
					}
					finish(clientSpan);
					return continuation;
				})
				.subscriberContext(c -> {
					if (log.isDebugEnabled()) {
						log.debug("Creating a client span for the WebClient");
					}
					Span parent = c.getOrDefault(Span.class, null);
					Span clientSpan = createNewSpan(request, parent);
					tracer().continueSpan(clientSpan);

					httpSpanInjector().inject(clientSpan, new ClientRequestTextMap(request, builder));
					if (log.isDebugEnabled()) {
						log.debug("Headers got injected from the client span " + clientSpan);
					}

					if (parent == null) {
						c = c.put(Span.class, clientSpan);
						if (log.isDebugEnabled()) {
							log.debug("Reactor Context got injected with the client span " + clientSpan);
						}
					}
					if (clientSpan != null && clientSpan.equals(tracer().getCurrentSpan())) {
						tracer().continueSpan(tracer().detach(clientSpan));
					}
					return c.put(CLIENT_SPAN_KEY, clientSpan);
				});
		return exchange;
	}

	/**
	 * Enriches the request with proper headers and publishes
	 * the client sent event
	 */
	private Span createNewSpan(ClientRequest request, Span optionalParent) {
		URI uri = request.url();
		String spanName = getName(uri);
		Span newSpan;
		if (optionalParent == null) {
			newSpan = tracer().createSpan(spanName);
		} else {
			newSpan = tracer().createSpan(spanName, optionalParent);
		}
		addRequestTags(request);
		newSpan.logEvent(Span.CLIENT_SEND);
		if (log.isDebugEnabled()) {
			log.debug("Starting new client span [" + newSpan + "]");
		}
		return newSpan;
	}

	private String getName(URI uri) {
		return SpanNameUtil.shorten(uriScheme(uri) + ":" + uri.getPath());
	}

	private String uriScheme(URI uri) {
		return uri.getScheme() == null ? "http" : uri.getScheme();
	}

	/**
	 * Adds HTTP tags to the client side span
	 */
	private void addRequestTags(ClientRequest request) {
		keysInjector().addRequestTags(request.url().toString(),
				request.url().getHost(),
				request.url().getPath(),
				request.method().name(),
				request.headers());
	}

	/**
	 * Close the current span and log the client received event
	 */
	private void finish(Span span) {
		tracer().continueSpan(span);
		if (log.isDebugEnabled()) {
			log.debug("Will close span and mark it with Client Received" + span);
		}
		span.logEvent(Span.CLIENT_RECV);
		tracer().close(span);
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	private HttpSpanInjector httpSpanInjector() {
		if (this.spanInjector == null) {
			this.spanInjector = this.beanFactory.getBean(HttpSpanInjector.class);
		}
		return this.spanInjector;
	}

	private HttpTraceKeysInjector keysInjector() {
		if (this.keysInjector == null) {
			this.keysInjector = this.beanFactory.getBean(HttpTraceKeysInjector.class);
		}
		return this.keysInjector;
	}

	private ErrorParser errorParser() {
		if (this.errorParser == null) {
			this.errorParser = this.beanFactory.getBean(ErrorParser.class);
		}
		return this.errorParser;
	}
}

class ClientRequestTextMap implements SpanTextMap {

	private final ClientRequest.Builder writeDelegate;
	private final ClientRequest readDelegate;

	ClientRequestTextMap(ClientRequest readDelegate,
			ClientRequest.Builder writeDelegate) {
		this.readDelegate = readDelegate;
		this.writeDelegate = writeDelegate;
	}

	@Override
	public Iterator<Map.Entry<String, String>> iterator() {
		final Iterator<Map.Entry<String, List<String>>> iterator = this.readDelegate.headers()
				.entrySet().iterator();
		return new Iterator<Map.Entry<String, String>>() {
			@Override public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override public Map.Entry<String, String> next() {
				Map.Entry<String, List<String>> next = iterator.next();
				List<String> value = next.getValue();
				return new AbstractMap.SimpleEntry<>(next.getKey(), value.isEmpty() ? "" : value.get(0));
			}
		};
	}

	@Override
	public void put(String key, String value) {
		if (!StringUtils.hasText(value)) {
			return;
		}
		this.writeDelegate.header(key, value);
	}
}