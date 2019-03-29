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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.handler.codec.http.HttpHeaders;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

class HttpClientBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	HttpClientBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof HttpClient) {
			return ((HttpClient) bean).mapConnect(new TracingMapConnect(this.beanFactory))
					.doOnRequest(TracingDoOnRequest.create(this.beanFactory))
					.doOnRequestError(TracingDoOnErrorRequest.create(this.beanFactory))
					.doOnResponse(TracingDoOnResponse.create(this.beanFactory))
					.doOnResponseError(TracingDoOnErrorResponse.create(this.beanFactory));
		}
		return bean;
	}

	private static class TracingMapConnect implements
			BiFunction<Mono<? extends Connection>, Bootstrap, Mono<? extends Connection>> {

		private final BeanFactory beanFactory;

		private Tracer tracer;

		TracingMapConnect(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public Mono<? extends Connection> apply(Mono<? extends Connection> mono,
				Bootstrap bootstrap) {
			return mono.subscriberContext(context -> context.put(AtomicReference.class,
					new AtomicReference<>(tracer().currentSpan())));
		}

		private Tracer tracer() {
			if (this.tracer == null) {
				this.tracer = this.beanFactory.getBean(Tracer.class);
			}
			return this.tracer;
		}

	}

	private static class TracingDoOnRequest
			implements BiConsumer<HttpClientRequest, Connection> {

		static final Propagation.Setter<HttpHeaders, String> SETTER = new Propagation.Setter<HttpHeaders, String>() {
			@Override
			public void put(HttpHeaders carrier, String key, String value) {
				if (!carrier.contains(key)) {
					carrier.add(key, value);
				}
			}

			@Override
			public String toString() {
				return "HttpHeaders::add";
			}
		};

		final BeanFactory beanFactory;

		HttpTracing httpTracing;

		Tracer tracer;

		HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

		TraceContext.Injector<HttpHeaders> injector;

		Propagation<String> propagation;

		TracingDoOnRequest(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		static TracingDoOnRequest create(BeanFactory beanFactory) {
			return new TracingDoOnRequest(beanFactory);
		}

		private HttpTracing httpTracing() {
			if (this.httpTracing == null) {
				this.httpTracing = this.beanFactory.getBean(HttpTracing.class);
			}
			return this.httpTracing;
		}

		private Propagation<String> propagation() {
			if (this.propagation == null) {
				this.propagation = httpTracing().tracing().propagation();
			}
			return this.propagation;
		}

		private TraceContext.Injector<HttpHeaders> injector() {
			if (this.injector == null) {
				this.injector = propagation().injector(SETTER);
			}
			return this.injector;
		}

		private HttpClientHandler<HttpClientRequest, HttpClientResponse> handler() {
			if (this.handler == null) {
				this.handler = HttpClientHandler.create(httpTracing(), new HttpAdapter());
			}
			return this.handler;
		}

		@Override
		public void accept(HttpClientRequest req, Connection connection) {
			if (propagation().keys().stream()
					.anyMatch(key -> req.requestHeaders().contains(key))) {
				// request already instrumented
				return;
			}
			AtomicReference reference = req.currentContext()
					.getOrDefault(AtomicReference.class, new AtomicReference());
			Span span = handler().handleSend(injector(), req.requestHeaders(), req,
					reference.get() == null ? handler().nextSpan(req)
							: (Span) reference.get());
			reference.set(span);
		}

	}

	private static class TracingDoOnResponse extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientResponse, Connection> {

		TracingDoOnResponse(BeanFactory beanFactory) {
			super(beanFactory);
		}

		static TracingDoOnResponse create(BeanFactory beanFactory) {
			return new TracingDoOnResponse(beanFactory);
		}

		@Override
		public void accept(HttpClientResponse httpClientResponse, Connection connection) {
			handle(httpClientResponse, null);
		}

	}

	private static class TracingDoOnErrorRequest extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientRequest, Throwable> {

		TracingDoOnErrorRequest(BeanFactory beanFactory) {
			super(beanFactory);
		}

		static TracingDoOnErrorRequest create(BeanFactory beanFactory) {
			return new TracingDoOnErrorRequest(beanFactory);
		}

		@Override
		public void accept(HttpClientRequest request, Throwable throwable) {
			handle(null, throwable);
		}

	}

	private static class TracingDoOnErrorResponse extends AbstractTracingDoOnHandler
			implements BiConsumer<HttpClientResponse, Throwable> {

		TracingDoOnErrorResponse(BeanFactory beanFactory) {
			super(beanFactory);
		}

		static TracingDoOnErrorResponse create(BeanFactory beanFactory) {
			return new TracingDoOnErrorResponse(beanFactory);
		}

		@Override
		public void accept(HttpClientResponse httpClientResponse, Throwable throwable) {
			handle(httpClientResponse, throwable);
		}

	}

	private static abstract class AbstractTracingDoOnHandler {

		final BeanFactory beanFactory;

		HttpTracing httpTracing;

		HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

		AbstractTracingDoOnHandler(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		private HttpTracing httpTracing() {
			if (this.httpTracing == null) {
				this.httpTracing = this.beanFactory.getBean(HttpTracing.class);
			}
			return this.httpTracing;
		}

		private HttpClientHandler<HttpClientRequest, HttpClientResponse> handler() {
			if (this.handler == null) {
				this.handler = HttpClientHandler.create(httpTracing(), new HttpAdapter());
			}
			return this.handler;
		}

		protected void handle(HttpClientResponse httpClientResponse,
				Throwable throwable) {
			if (httpClientResponse == null) {
				return;
			}
			AtomicReference reference = httpClientResponse.currentContext()
					.getOrDefault(AtomicReference.class, null);
			if (reference == null || reference.get() == null) {
				return;
			}
			handler().handleReceive(httpClientResponse, throwable,
					(Span) reference.get());
		}

	}

	private static class HttpAdapter
			extends brave.http.HttpClientAdapter<HttpClientRequest, HttpClientResponse> {

		@Override
		public String method(HttpClientRequest request) {
			return request.method().name();
		}

		@Override
		public String url(HttpClientRequest request) {
			return request.uri();
		}

		@Override
		public String requestHeader(HttpClientRequest request, String name) {
			Object result = request.requestHeaders().get(name);
			return result != null ? result.toString() : "";
		}

		@Override
		public Integer statusCode(HttpClientResponse response) {
			return response.status().code();
		}

	}

}
