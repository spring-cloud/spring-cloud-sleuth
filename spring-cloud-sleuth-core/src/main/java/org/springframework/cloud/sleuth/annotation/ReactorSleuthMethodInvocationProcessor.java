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

package org.springframework.cloud.sleuth.annotation;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Supplier;

import brave.Span;
import brave.Tracer;
import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Publisher;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Method Invocation Processor for Reactor.
 *
 * @author Marcin Grzejszczak
 * @since 2.1.0
 */
class ReactorSleuthMethodInvocationProcessor
		extends AbstractSleuthMethodInvocationProcessor {

	private NonReactorSleuthMethodInvocationProcessor nonReactorSleuthMethodInvocationProcessor;

	@Override
	public Object process(MethodInvocation invocation, NewSpan newSpan,
			ContinueSpan continueSpan) throws Throwable {
		Method method = invocation.getMethod();
		if (isReactorReturnType(method.getReturnType())) {
			return proceedUnderReactorSpan(invocation, newSpan, continueSpan);
		}
		else {
			return nonReactorSleuthMethodInvocationProcessor().process(invocation,
					newSpan, continueSpan);
		}
	}

	private Object proceedUnderReactorSpan(MethodInvocation invocation, NewSpan newSpan,
			ContinueSpan continueSpan) throws Throwable {
		Span spanPrevious = tracer().currentSpan();
		// in case of @ContinueSpan and no span in tracer we start new span and should
		// close it on completion
		boolean startNewSpan = newSpan != null || spanPrevious == null;
		Span span;
		if (startNewSpan) {
			span = tracer().nextSpan();
			newSpanParser().parse(invocation, newSpan, span);
		}
		else {
			span = spanPrevious;
		}
		String log = log(continueSpan);
		boolean hasLog = StringUtils.hasText(log);
		try (Tracer.SpanInScope ws = tracer().withSpanInScope(span)) {
			Publisher<?> publisher = (Publisher) invocation.proceed();
			Mono<Span> startSpan = Mono.defer(() -> withSpanInScope(span, () -> {
				if (startNewSpan) {
					span.start();
				}
				before(invocation, span, log, hasLog);
				return Mono.just(span);
			}));
			if (publisher instanceof Mono) {
				return startSpan
						.flatMap(spanStarted -> ((Mono<?>) publisher)
								.doOnError(onFailureReactor(log, hasLog, spanStarted))
								.doFinally(afterReactor(startNewSpan, log, hasLog,
										spanStarted)))
						// put span in context so it can be used by
						// ScopePassingSpanSubscriber
						.subscriberContext(context -> context.put(Span.class, span));
			}
			else if (publisher instanceof Flux) {
				return startSpan
						.flatMapMany(spanStarted -> ((Flux<?>) publisher)
								.doOnError(onFailureReactor(log, hasLog, spanStarted))
								.doFinally(afterReactor(startNewSpan, log, hasLog,
										spanStarted)))
						// put span in context so it can be used by
						// ScopePassingSpanSubscriber
						.subscriberContext(context -> context.put(Span.class, span));
			}
			else {
				throw new IllegalArgumentException(
						"Unexpected type of publisher: " + publisher.getClass());
			}
		}
	}

	private <T> T withSpanInScope(Span span, Supplier<T> supplier) {
		try (Tracer.SpanInScope ws1 = tracer().withSpanInScope(span)) {
			return supplier.get();
		}
	}

	private Consumer<SignalType> afterReactor(boolean isNewSpan, String log,
			boolean hasLog, Span span) {
		return signalType -> {
			try (Tracer.SpanInScope ws = tracer().withSpanInScope(span)) {
				after(span, isNewSpan, log, hasLog);
			}
		};
	}

	private Consumer<Throwable> onFailureReactor(String log, boolean hasLog, Span span) {
		return throwable -> {
			try (Tracer.SpanInScope ws = tracer().withSpanInScope(span)) {
				onFailure(span, log, hasLog, throwable);
			}
		};
	}

	private boolean isReactorReturnType(Class<?> returnType) {
		return Flux.class.equals(returnType) || Mono.class.equals(returnType);
	}

	private NonReactorSleuthMethodInvocationProcessor nonReactorSleuthMethodInvocationProcessor() {
		if (this.nonReactorSleuthMethodInvocationProcessor == null) {
			this.nonReactorSleuthMethodInvocationProcessor = new NonReactorSleuthMethodInvocationProcessor();
			this.nonReactorSleuthMethodInvocationProcessor
					.setBeanFactory(this.beanFactory);
		}
		return this.nonReactorSleuthMethodInvocationProcessor;
	}

}
