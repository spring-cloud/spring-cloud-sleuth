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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.util.annotation.Nullable;
import reactor.util.context.Context;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.api.http.HttpServerRequest;
import org.springframework.cloud.sleuth.api.http.HttpServerResponse;
import org.springframework.cloud.sleuth.instrument.reactor.SleuthReactorProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/**
 * A {@link WebFilter} that creates / continues / closes and detaches spans for a reactive
 * web application.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
final class TraceWebFilter implements WebFilter, Ordered {

	// Remember that this can be used in other packages
	protected static final String TRACE_REQUEST_ATTR = Span.class.getName();

	static final String MVC_CONTROLLER_CLASS_KEY = "mvc.controller.class";
	static final String MVC_CONTROLLER_METHOD_KEY = "mvc.controller.method";

	private static final Log log = LogFactory.getLog(TraceWebFilter.class);

	private static final String STATUS_CODE_KEY = "http.status_code";

	private static final String TRACE_SPAN_WITHOUT_PARENT = TraceWebFilter.class.getName() + ".SPAN_WITH_NO_PARENT";

	private final BeanFactory beanFactory;

	Tracer tracer;

	HttpServerHandler handler;

	SleuthWebProperties webProperties;

	SleuthReactorProperties sleuthReactorProperties;

	TraceWebFilter(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	public static WebFilter create(BeanFactory beanFactory) {
		return new TraceWebFilter(beanFactory);
	}

	@SuppressWarnings("unchecked")
	HttpServerHandler handler() {
		if (this.handler == null) {
			this.handler = this.beanFactory.getBean(HttpServerHandler.class);
		}
		return this.handler;
	}

	Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	SleuthWebProperties sleuthWebProperties() {
		if (this.webProperties == null) {
			this.webProperties = this.beanFactory.getBean(SleuthWebProperties.class);
		}
		return this.webProperties;
	}

	SleuthReactorProperties sleuthReactorProperties() {
		if (this.sleuthReactorProperties == null) {
			this.sleuthReactorProperties = this.beanFactory.getBean(SleuthReactorProperties.class);
		}
		return this.sleuthReactorProperties;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String uri = exchange.getRequest().getPath().pathWithinApplication().value();
		if (log.isDebugEnabled()) {
			log.debug("Received a request to uri [" + uri + "]");
		}
		Mono<Void> source = chain.filter(exchange);
		boolean tracePresent = isTracePresent();
		return new MonoWebFilterTrace(source, exchange, tracePresent, this);
	}

	private boolean isTracePresent() {
		if (sleuthReactorProperties().getInstrumentationType() == SleuthReactorProperties.InstrumentationType.MANUAL) {
			return false;
		}
		boolean tracePresent = tracer().currentSpan() != null;
		if (tracePresent) {
			// clear any previous trace
			tracer().withSpan(null); // TODO: dangerous and also allocates stuff
		}
		return tracePresent;
	}

	@Override
	public int getOrder() {
		return sleuthWebProperties().getFilterOrder();
	}

	private static class MonoWebFilterTrace extends MonoOperator<Void, Void> {

		final ServerWebExchange exchange;

		final Tracer tracer;

		final Span span;

		final HttpServerHandler handler;

		final AtomicBoolean initialSpanAlreadyRemoved = new AtomicBoolean();

		final boolean initialTracePresent;

		MonoWebFilterTrace(Mono<? extends Void> source, ServerWebExchange exchange, boolean initialTracePresent,
				TraceWebFilter parent) {
			super(source);
			this.tracer = parent.tracer();
			this.handler = parent.handler();
			this.exchange = exchange;
			this.span = exchange.getAttribute(TRACE_REQUEST_ATTR);
			this.initialTracePresent = initialTracePresent;
		}

		@Override
		public void subscribe(CoreSubscriber<? super Void> subscriber) {
			Context context = contextWithoutInitialSpan(subscriber.currentContext());
			this.source.subscribe(new WebFilterTraceSubscriber(subscriber, context, findOrCreateSpan(context), this));
		}

		private Context contextWithoutInitialSpan(Context context) {
			if (this.initialTracePresent && !this.initialSpanAlreadyRemoved.get()) {
				context = context.delete(Span.class);
				this.initialSpanAlreadyRemoved.set(true);
			}
			return context;
		}

		private Span findOrCreateSpan(Context c) {
			Span span;
			if (c.hasKey(Span.class)) {
				Span parent = c.get(Span.class);
				try (Tracer.SpanInScope spanInScope = this.tracer.withSpan(parent)) {
					span = this.tracer.nextSpan();
				}
				if (log.isDebugEnabled()) {
					log.debug("Found span in reactor context" + span);
				}
			}
			else {
				if (this.span != null) {
					try (Tracer.SpanInScope spanInScope = this.tracer.withSpan(this.span)) {
						span = this.tracer.nextSpan();
					}
					if (log.isDebugEnabled()) {
						log.debug("Found span in attribute " + span);
					}
				}
				else {
					span = this.handler.handleReceive(new WrappedRequest(this.exchange.getRequest()));
					if (log.isDebugEnabled()) {
						log.debug("Handled receive of span " + span);
					}
				}
				this.exchange.getAttributes().put(TRACE_REQUEST_ATTR, span);
			}
			return span;
		}

		static final class WebFilterTraceSubscriber implements CoreSubscriber<Void> {

			final CoreSubscriber<? super Void> actual;

			final Context context;

			final Span span;

			final ServerWebExchange exchange;

			final HttpServerHandler handler;

			WebFilterTraceSubscriber(CoreSubscriber<? super Void> actual, Context context, Span span,
					MonoWebFilterTrace parent) {
				this.actual = actual;
				this.span = span;
				this.context = context.put(TraceContext.class, span.context());
				this.exchange = parent.exchange;
				this.handler = parent.handler;
			}

			@Override
			public void onSubscribe(Subscription subscription) {
				this.actual.onSubscribe(subscription);
			}

			@Override
			public void onNext(Void aVoid) {
				// IGNORE
			}

			@Override
			public void onError(Throwable t) {
				terminateSpan(t);
				this.actual.onError(t);
			}

			@Override
			public void onComplete() {
				terminateSpan(null);
				this.actual.onComplete();
			}

			@Override
			public Context currentContext() {
				return this.context;
			}

			private void terminateSpan(@Nullable Throwable t) {
				Object attribute = this.exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
				addClassMethodTag(attribute, this.span);
				addClassNameTag(attribute, this.span);
				Object pattern = this.exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
				String httpRoute = pattern != null ? pattern.toString() : "";
				addResponseTagsForSpanWithoutParent(this.exchange, this.exchange.getResponse(), this.span);
				WrappedResponse response = new WrappedResponse(this.exchange.getResponse(),
						this.exchange.getRequest().getMethodValue(), httpRoute, t);
				this.handler.handleSend(response, this.span);
				if (log.isDebugEnabled()) {
					log.debug("Handled send of " + this.span);
				}
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
				if (handler == null) {
					return;
				}
				String className;
				if (handler instanceof HandlerMethod) {
					className = ((HandlerMethod) handler).getBeanType().getSimpleName();
				}
				else {
					className = handler.getClass().getSimpleName();
				}
				if (log.isDebugEnabled()) {
					log.debug("Adding a class tag with value [" + className + "] to a span " + span);
				}
				span.tag(MVC_CONTROLLER_CLASS_KEY, className);
			}

			private void addResponseTagsForSpanWithoutParent(ServerWebExchange exchange, ServerHttpResponse response,
					Span span) {
				if (spanWithoutParent(exchange) && response.getStatusCode() != null && span != null) {
					span.tag(STATUS_CODE_KEY, String.valueOf(response.getStatusCode().value()));
				}
			}

			private boolean spanWithoutParent(ServerWebExchange exchange) {
				return exchange.getAttribute(TRACE_SPAN_WITHOUT_PARENT) != null;
			}

		}

	}

	static final class WrappedRequest implements HttpServerRequest {

		final ServerHttpRequest delegate;

		WrappedRequest(ServerHttpRequest delegate) {
			this.delegate = delegate;
		}

		@Override
		public Collection<String> headerNames() {
			return this.delegate.getHeaders().keySet();
		}

		@Override
		public ServerHttpRequest unwrap() {
			return delegate;
		}

		@Override
		public String method() {
			return delegate.getMethodValue();
		}

		@Override
		public String path() {
			return delegate.getPath().toString();
		}

		@Override
		public String url() {
			return delegate.getURI().toString();
		}

		@Override
		public String header(String name) {
			return delegate.getHeaders().getFirst(name);
		}

	}

	static final class WrappedResponse implements HttpServerResponse {

		final ServerHttpResponse delegate;

		final String method;

		final String httpRoute;

		final Throwable throwable;

		WrappedResponse(ServerHttpResponse resp, String method, String httpRoute, Throwable throwable) {
			this.delegate = resp;
			this.method = method;
			this.httpRoute = httpRoute;
			this.throwable = throwable;
		}

		@Override
		public String method() {
			return method;
		}

		@Override
		public String route() {
			return httpRoute;
		}

		@Override
		public ServerHttpResponse unwrap() {
			return delegate;
		}

		@Override
		public int statusCode() {
			HttpStatus statusCode = delegate.getStatusCode();
			return statusCode != null ? statusCode.value() : 0;
		}

		@Override
		public Collection<String> headerNames() {
			return this.delegate.getHeaders().keySet();
		}

		@Override
		public Throwable error() {
			return this.throwable;
		}

	}

}
