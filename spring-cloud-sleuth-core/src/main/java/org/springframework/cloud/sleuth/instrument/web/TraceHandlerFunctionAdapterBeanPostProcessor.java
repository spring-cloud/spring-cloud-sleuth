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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.concurrent.atomic.AtomicReference;

import brave.Span;
import brave.propagation.CurrentTraceContext;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.reactive.HandlerAdapter;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.support.HandlerFunctionAdapter;
import org.springframework.web.server.ServerWebExchange;

class TraceHandlerFunctionAdapterBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	TraceHandlerFunctionAdapterBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof HandlerFunctionAdapter) {
			return new TraceHandlerAdapter((HandlerAdapter) bean, this.beanFactory);
		}
		return bean;
	}

	private static final class TraceHandlerAdapter implements HandlerAdapter {

		private final BeanFactory beanFactory;

		private final HandlerAdapter delegate;

		private TraceHandlerAdapter(HandlerAdapter delegate, BeanFactory beanFactory) {
			this.delegate = delegate;
			this.beanFactory = beanFactory;
		}

		@Override
		public boolean supports(Object handler) {
			return this.delegate.supports(handler);
		}

		@Override
		public Mono<HandlerResult> handle(ServerWebExchange exchange, Object handler) {
			HandlerFunction<?> handlerFunction = (HandlerFunction<?>) handler;
			TraceHandlerFunction traceHandlerFunction = new TraceHandlerFunction(
					handlerFunction, this.beanFactory);
			return this.delegate.handle(exchange, traceHandlerFunction);
		}

	}

	private static final class TraceHandlerFunction implements HandlerFunction {

		private final HandlerFunction<?> delegate;

		private final BeanFactory beanFactory;

		private CurrentTraceContext currentTraceContext;

		private TraceHandlerFunction(HandlerFunction<?> delegate,
				BeanFactory beanFactory) {
			this.delegate = delegate;
			this.beanFactory = beanFactory;
		}

		@Override
		public Mono<?> handle(ServerRequest serverRequest) {
			AtomicReference<CurrentTraceContext.Scope> scope = new AtomicReference<>();
			// @formatter:off
			return Mono.defer(() -> {
						serverRequest
							.attribute(TraceWebFilter.TRACE_REQUEST_ATTR)
							.ifPresent(span -> scope.set(
								currentTraceContext().maybeScope(((Span) span).context())));
						return this.delegate.handle(serverRequest);
					})
					.doFinally(signalType -> {
						CurrentTraceContext.Scope spanInScope = scope.get();
						if (spanInScope != null) {
							spanInScope.close();
						}
					});
			// @formatter:on
		}

		private CurrentTraceContext currentTraceContext() {
			if (this.currentTraceContext == null) {
				this.currentTraceContext = this.beanFactory
						.getBean(CurrentTraceContext.class);
			}
			return this.currentTraceContext;
		}

	}

}
