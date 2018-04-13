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

package org.springframework.cloud.sleuth.instrument.web;

import brave.Span;
import brave.Tracer;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * A {@link WebFilter} that creates / continues / closes and detaches spans
 * for a reactive web application.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public final class TraceWebFilter implements WebFilter, Ordered {

	private static final Log log = LogFactory.getLog(TraceWebFilter.class);
	private static final String STATUS_CODE_KEY = "http.status_code";
	static final String MVC_CONTROLLER_CLASS_KEY = "mvc.controller.class";
	static final String MVC_CONTROLLER_METHOD_KEY = "mvc.controller.method";

	protected static final String TRACE_REQUEST_ATTR = TraceWebFilter.class.getName()
			+ ".TRACE";
	private static final String TRACE_SPAN_WITHOUT_PARENT = TraceWebFilter.class.getName()
			+ ".SPAN_WITH_NO_PARENT";

	/**
	 * If you register your filter before the {@link TraceWebFilter} then you will not
	 * have the tracing context passed for you out of the box. That means that e.g. your
	 * logs will not get correlated.
	 */
	public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

	static final Propagation.Getter<HttpHeaders, String> GETTER =
			new Propagation.Getter<HttpHeaders, String>() {

				@Override public String get(HttpHeaders carrier, String key) {
					return carrier.getFirst(key);
				}

				@Override public String toString() {
					return "HttpHeaders::getFirst";
				}
			};

	public static WebFilter create(BeanFactory beanFactory) {
		return new TraceWebFilter(beanFactory);
	}

	Tracer tracer;
	HttpServerHandler<ServerHttpRequest, ServerHttpResponse> handler;
	TraceContext.Extractor<HttpHeaders> extractor;
	private final BeanFactory beanFactory;

	TraceWebFilter(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@SuppressWarnings("unchecked")
	HttpServerHandler<ServerHttpRequest, ServerHttpResponse> handler() {
		if (this.handler == null) {
			this.handler = HttpServerHandler
					.create(this.beanFactory.getBean(HttpTracing.class),
							new TraceWebFilter.HttpAdapter());
		}
		return this.handler;
	}

	Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(HttpTracing.class).tracing().tracer();
		}
		return this.tracer;
	}

	TraceContext.Extractor<HttpHeaders> extractor() {
		if (this.extractor == null) {
			this.extractor = this.beanFactory.getBean(HttpTracing.class)
					.tracing().propagation().extractor(GETTER);
		}
		return this.extractor;
	}

	@Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		if (tracer().currentSpan() != null) {
			// clear any previous trace
			tracer().withSpanInScope(null);
		}
		String uri = exchange.getRequest().getPath().pathWithinApplication().value();
		if (log.isDebugEnabled()) {
			log.debug("Received a request to uri [" + uri + "]");
		}
		Span spanFromAttribute = getSpanFromAttribute(exchange);
		final String CONTEXT_ERROR = "sleuth.webfilter.context.error";
		return chain
				.filter(exchange)
				.compose(f -> f.then(Mono.subscriberContext())
						.onErrorResume(t -> Mono.subscriberContext()
								.map(c -> c.put(CONTEXT_ERROR, t)))
						.flatMap(c -> {
							//reactivate span from context
							Span span = spanFromContext(c);
							Mono<Void> continuation;
							Throwable t = null;
							if (c.hasKey(CONTEXT_ERROR)) {
								t = c.get(CONTEXT_ERROR);
								continuation = Mono.error(t);
							} else {
								continuation = Mono.empty();
							}
							String httpRoute = null;
							Object attribute = exchange
									.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
							if (attribute instanceof HandlerMethod) {
								HandlerMethod handlerMethod = (HandlerMethod) attribute;
								addClassMethodTag(handlerMethod, span);
								addClassNameTag(handlerMethod, span);
								Object pattern = exchange
										.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
								httpRoute = pattern != null ? pattern.toString() : "";
							}
							addResponseTagsForSpanWithoutParent(exchange, exchange.getResponse(), span);
							DecoratedServerHttpResponse delegate = new DecoratedServerHttpResponse(
									exchange.getResponse(), exchange.getRequest().getMethodValue(),
									httpRoute);
							handler().handleSend(delegate, t, span);
							if (log.isDebugEnabled()) {
								log.debug("Handled send of " + span);
							}
							return continuation;
						})
						.subscriberContext(c -> {
							Span span;
							if (c.hasKey(Span.class)) {
								Span parent = c.get(Span.class);
								span = tracer()
										.nextSpan(TraceContextOrSamplingFlags.create(parent.context()))
										.start();
								if (log.isDebugEnabled()) {
									log.debug("Found span in reactor context" + span);
								}
							} else {
								if (spanFromAttribute != null) {
									span = spanFromAttribute;
									if (log.isDebugEnabled()) {
										log.debug("Found span in attribute " + span);
									}
								} else {
									span = handler().handleReceive(extractor(),
											exchange.getRequest().getHeaders(), exchange.getRequest());
									if (log.isDebugEnabled()) {
										log.debug("Handled receive of span " + span);
									}
								}
								exchange.getAttributes().put(TRACE_REQUEST_ATTR, span);
							}
							return c.put(Span.class, span);
						}));
	}

	private Span spanFromContext(Context c) {
		if (c.hasKey(Span.class)) {
			Span span = c.get(Span.class);
			if (log.isDebugEnabled()) {
				log.debug("Found span in context " + span);
			}
			return span;
		}
		Span span = defaultSpan();
		if (log.isDebugEnabled()) {
			log.debug("No span found in context. Creating a new one " + span);
		}
		return span;
	}

	private Span defaultSpan() {
		return tracer().nextSpan().start();
	}

	private void addResponseTagsForSpanWithoutParent(ServerWebExchange exchange,
			ServerHttpResponse response, Span span) {
		if (spanWithoutParent(exchange) && response.getStatusCode() != null
				&& span != null) {
			span.tag(STATUS_CODE_KEY, String.valueOf(response.getStatusCode().value()));
		}
	}

	private Span getSpanFromAttribute(ServerWebExchange exchange) {
		return exchange.getAttribute(TRACE_REQUEST_ATTR);
	}

	private boolean spanWithoutParent(ServerWebExchange exchange) {
		return exchange.getAttribute(TRACE_SPAN_WITHOUT_PARENT) != null;
	}

	private void addClassMethodTag(Object handler, Span span) {
		if (handler instanceof HandlerMethod) {
			String methodName = ((HandlerMethod) handler).getMethod().getName();
			span.tag(MVC_CONTROLLER_METHOD_KEY, methodName);
			if (log.isDebugEnabled()) {
				log.debug("Adding a method tag with value [" + methodName + "] to a span " + span);
			}
		}
	}

	private void addClassNameTag(Object handler, Span span) {
		String className;
		if (handler instanceof HandlerMethod) {
			className = ((HandlerMethod) handler).getBeanType().getSimpleName();
		} else {
			className = handler.getClass().getSimpleName();
		}
		if (log.isDebugEnabled()) {
			log.debug("Adding a class tag with value [" + className + "] to a span " + span);
		}
		span.tag(MVC_CONTROLLER_CLASS_KEY, className);
	}

	@Override public int getOrder() {
		return ORDER;
	}

	static final class DecoratedServerHttpResponse extends ServerHttpResponseDecorator {

		final String method, httpRoute;

		DecoratedServerHttpResponse(ServerHttpResponse delegate, String method, String httpRoute) {
			super(delegate);
			this.method = method;
			this.httpRoute = httpRoute;
		}
	}

	static final class HttpAdapter
			extends brave.http.HttpServerAdapter<ServerHttpRequest, ServerHttpResponse> {

		@Override public String method(ServerHttpRequest request) {
			return request.getMethodValue();
		}

		@Override public String url(ServerHttpRequest request) {
			return request.getURI().toString();
		}

		@Override public String requestHeader(ServerHttpRequest request, String name) {
			Object result = request.getHeaders().getFirst(name);
			return result != null ? result.toString() : null;
		}

		@Override public Integer statusCode(ServerHttpResponse response) {
			return response.getStatusCode() != null ?
					response.getStatusCode().value() : null;
		}

		@Override public String methodFromResponse(ServerHttpResponse response) {
			if (response instanceof DecoratedServerHttpResponse) {
				return ((DecoratedServerHttpResponse) response).method;
			}
			return null;
		}

		@Override public String route(ServerHttpResponse response) {
			if (response instanceof DecoratedServerHttpResponse) {
				return ((DecoratedServerHttpResponse) response).httpRoute;
			}
			return null;
		}
	}
}

