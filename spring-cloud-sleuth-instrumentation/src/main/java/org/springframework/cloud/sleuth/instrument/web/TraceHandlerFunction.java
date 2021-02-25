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

import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * Tracing representation of a {@link HandlerFunction}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.2
 */
public class TraceHandlerFunction implements HandlerFunction {

	private final HandlerFunction<?> delegate;

	private final BeanFactory beanFactory;

	private CurrentTraceContext currentTraceContext;

	public TraceHandlerFunction(HandlerFunction<?> delegate, BeanFactory beanFactory) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
	}

	@Override
	public Mono<?> handle(ServerRequest serverRequest) {
		AtomicReference<CurrentTraceContext.Scope> scope = new AtomicReference<>();
		return Mono.just(scope)
				.doFirst(() -> serverRequest.attribute(TraceWebFilter.TRACE_REQUEST_ATTR)
						.ifPresent(span -> scope.set(currentTraceContext().maybeScope(((Span) span).context()))))
				.flatMap(r -> this.delegate.handle(serverRequest)).doFinally(signalType -> {
					CurrentTraceContext.Scope spanInScope = scope.get();
					if (spanInScope != null) {
						spanInScope.close();
					}
				});
	}

	private CurrentTraceContext currentTraceContext() {
		if (this.currentTraceContext == null) {
			this.currentTraceContext = this.beanFactory.getBean(CurrentTraceContext.class);
		}
		return this.currentTraceContext;
	}

}
