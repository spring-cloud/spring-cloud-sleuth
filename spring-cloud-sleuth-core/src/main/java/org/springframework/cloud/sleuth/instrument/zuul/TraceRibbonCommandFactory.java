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

package org.springframework.cloud.sleuth.instrument.zuul;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Future;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.netflix.ribbon.support.RibbonCommandContext;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommand;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;
import org.springframework.http.client.ClientHttpResponse;
import rx.Observable;

/**
 * Propagates traces downstream via http headers that contain trace metadata.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
class TraceRibbonCommandFactory implements RibbonCommandFactory,
		SmartInitializingSingleton {

	static final Propagation.Setter<RibbonCommandContext, String> SETTER = new Propagation.Setter<RibbonCommandContext, String>() {
		@Override public void put(RibbonCommandContext carrier, String key, String value) {
			carrier.getHeaders().put(key, Collections.singletonList(value));
		}

		@Override public String toString() {
			return "RibbonCommandContext::headers::put";
		}
	};

	private static final Log log = LogFactory.getLog(TraceRibbonCommandFactory.class);

	HttpTracing tracing;
	Tracer tracer;
	RibbonCommandFactory delegate;
	HttpClientHandler<RibbonCommandContext, ClientHttpResponse> handler;
	TraceContext.Injector<RibbonCommandContext> injector;
	final BeanFactory beanFactory;

	TraceRibbonCommandFactory(RibbonCommandFactory delegate, BeanFactory beanFactory) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
	}

	private void initialize() {
		if (this.tracing == null) {
			this.tracing = httpTracing();
		}
		if (this.tracer == null) {
			this.tracer = httpTracing().tracing().tracer();
		}
		if (this.handler == null) {
			this.handler = HttpClientHandler
					.create(httpTracing(), new TraceRibbonCommandFactory.HttpAdapter());
		}
		if (this.injector == null) {
			this.injector = httpTracing().tracing().propagation().injector(SETTER);
		}
	}

	private HttpTracing httpTracing() {
		if (this.tracing == null) {
			this.tracing = this.beanFactory.getBean(HttpTracing.class);
		}
		return this.tracing;
	}

	@Override
	public RibbonCommand create(final RibbonCommandContext context) {
		// just in case - everything should be already initialized
		initialize();
		final RibbonCommand ribbonCommand = this.delegate.create(context);
		Span span = this.tracer.currentSpan();
		if (log.isDebugEnabled()) {
			log.debug("Will set contents of the span " + this.tracer.currentSpan() + " in the ribbon command");
		}
		return new RibbonCommand() {
			@Override public ClientHttpResponse execute() {
				Span span = TraceRibbonCommandFactory.this.handler.handleSend(TraceRibbonCommandFactory.this.injector, context);
				ClientHttpResponse response = null;
				Throwable error = null;
				try (Tracer.SpanInScope ws = TraceRibbonCommandFactory.this.tracer.withSpanInScope(span)) {
					return response = ribbonCommand.execute();
				} catch (RuntimeException | Error e) {
					if (log.isDebugEnabled()) {
						log.debug("Exception occurred while trying to execute ribbon command", e);
					}
					error = e;
					throw e;
				} finally {
					TraceRibbonCommandFactory.this.handler.handleReceive(response, error, span);
				}
			}

			// currently only .execute() is used in Zuul
			@Override public Future<ClientHttpResponse> queue() {
				parseRequest(context, span);
				return ribbonCommand.queue();
			}

			// currently only .execute() is used in Zuul
			@Override public Observable<ClientHttpResponse> observe() {
				parseRequest(context, span);
				return ribbonCommand.observe();
			}
		};

	}

	private void parseRequest(RibbonCommandContext context, Span span) {
		TraceRibbonCommandFactory.this.tracing.clientParser()
				.request(new TraceRibbonCommandFactory.HttpAdapter(), context, span);
	}

	@Override public void afterSingletonsInstantiated() {
		initialize();
	}

	static final class HttpAdapter
			extends brave.http.HttpClientAdapter<RibbonCommandContext, ClientHttpResponse> {

		@Override public String method(RibbonCommandContext request) {
			return request.getMethod();
		}

		@Override public String url(RibbonCommandContext request) {
			return request.getUri();
		}

		@Override public String requestHeader(RibbonCommandContext request, String name) {
			Object result = request.getHeaders().getFirst(name);
			return result != null ? result.toString() : null;
		}

		@Override public Integer statusCode(ClientHttpResponse response) {
			try {
				return response.getRawStatusCode();
			} catch (IOException e) {
				return null;
			}
		}
	}
}
