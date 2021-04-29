/*
 * Copyright 2013-2021 the original author or authors.
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

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.web.reactive.HandlerAdapter;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.server.ServerWebExchange;

/**
 * Tracing representation of a {@link HandlerAdapter}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.2
 */
public class TraceHandlerAdapter implements HandlerAdapter {

	private final BeanFactory beanFactory;

	private final HandlerAdapter delegate;

	public TraceHandlerAdapter(HandlerAdapter delegate, BeanFactory beanFactory) {
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
		TraceHandlerFunction traceHandlerFunction = new TraceHandlerFunction(handlerFunction, this.beanFactory);
		return this.delegate.handle(exchange, traceHandlerFunction);
	}

}
