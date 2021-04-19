/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.circuitbreaker;

import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;

class TraceReactiveCircuitBreaker implements ReactiveCircuitBreaker {

	private static final Log log = LogFactory.getLog(TraceReactiveCircuitBreaker.class);

	private final ReactiveCircuitBreaker delegate;

	private final Tracer tracer;

	private final CurrentTraceContext currentTraceContext;

	TraceReactiveCircuitBreaker(ReactiveCircuitBreaker delegate, Tracer tracer,
			CurrentTraceContext currentTraceContext) {
		this.delegate = delegate;
		this.tracer = tracer;
		this.currentTraceContext = currentTraceContext;
	}

	@Override
	public <T> Mono<T> run(Mono<T> toRun) {
		return runAndTrace(this.delegate.run(toRun));
	}

	@Override
	public <T> Mono<T> run(Mono<T> toRun, Function<Throwable, Mono<T>> fallback) {
		return runAndTrace(
				this.delegate.run(toRun, fallback != null ? new TraceFunction<>(this.tracer, fallback) : null));
	}

	@Override
	public <T> Flux<T> run(Flux<T> toRun) {
		return runAndTrace(this.delegate.run(toRun));
	}

	@Override
	public <T> Flux<T> run(Flux<T> toRun, Function<Throwable, Flux<T>> fallback) {
		return runAndTrace(
				this.delegate.run(toRun, fallback != null ? new TraceFunction<>(this.tracer, fallback) : null));
	}

	private <T> Mono<T> runAndTrace(Mono<T> mono) {
		return Mono.deferContextual(contextView -> {
			Span span = contextView.get(Span.class);
			Tracer.SpanInScope scope = contextView.get(Tracer.SpanInScope.class);
			return mono.doOnError(span::error).doFinally(signalType -> {
				span.end();
				scope.close();
			});
		}).contextWrite(this::enhanceContext);
	}

	private <T> Flux<T> runAndTrace(Flux<T> flux) {
		return Flux.deferContextual(contextView -> {
			Span span = contextView.get(Span.class);
			Tracer.SpanInScope scope = contextView.get(Tracer.SpanInScope.class);
			return flux.doOnError(span::error).doFinally(signalType -> {
				span.end();
				scope.close();
			});
		}).contextWrite(this::enhanceContext);
	}

	private Span spanFromContext(reactor.util.context.Context context) {
		TraceContext traceContext = context.getOrDefault(TraceContext.class, null);
		Span span = null;
		if (traceContext == null) {
			span = context.getOrDefault(Span.class, null);
		}
		if (traceContext == null && span == null) {
			span = this.tracer.nextSpan();
			if (log.isDebugEnabled()) {
				log.debug("There was no previous span in reactor context, created a new one [" + span + "]");
			}
		}
		else if (traceContext != null) {
			// there was a previous span - we create a child one
			try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(traceContext)) {
				if (log.isDebugEnabled()) {
					log.debug("Found a trace context in reactor context [" + traceContext + "]");
				}
				span = this.tracer.nextSpan();
				if (log.isDebugEnabled()) {
					log.debug("Created a child span [" + span + "]");
				}
			}
		}
		else {
			if (log.isDebugEnabled()) {
				log.debug("Found a span in reactor context [" + span + "]");
			}
			span = this.tracer.nextSpan(span);
			if (log.isDebugEnabled()) {
				log.debug("Created a child span [" + span + "]");
			}
		}
		// TODO: Better name?
		return span.name("function");
	}

	private Context enhanceContext(Context context) {
		Span span = spanFromContext(context);
		return context.put(Span.class, span).put(TraceContext.class, span.context()).put(Tracer.SpanInScope.class,
				this.tracer.withSpan(span));
	}

}
