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

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;
import reactor.util.context.Context;

import org.springframework.web.server.ServerWebExchange;

/**
 * WebFlux operators that are capable to reuse tracing context from Reactor's Context.
 * IMPORTANT: This API is experimental and might change in the future.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public final class WebFluxSleuthOperators {

	private static final Log log = LogFactory.getLog(WebFluxSleuthOperators.class);

	private WebFluxSleuthOperators() {
		throw new IllegalStateException("You can't instantiate a utility class");
	}

	/**
	 * Wraps a runnable with a span.
	 * @param signalType - Reactor's signal type
	 * @param runnable - lambda to execute within the tracing context
	 * @return consumer of a signal
	 */
	public static Consumer<Signal> withSpanInScope(SignalType signalType, Runnable runnable) {
		return signal -> {
			if (signalType != signal.getType()) {
				return;
			}
			withSpanInScope(runnable).accept(signal);
		};
	}

	/**
	 * Wraps a runnable with a span.
	 * @param signalType - Reactor's signal type
	 * @param consumer - lambda to execute within the tracing context
	 * @return consumer of a signal
	 */
	public static Consumer<Signal> withSpanInScope(SignalType signalType, Consumer<Signal> consumer) {
		return signal -> {
			if (signalType != signal.getType()) {
				return;
			}
			withSpanInScope(signal.getContext(), () -> consumer.accept(signal));
		};
	}

	/**
	 * Wraps a runnable with a span.
	 * @param runnable - lambda to execute within the tracing context
	 * @return consumer of a signal
	 */
	public static Consumer<Signal> withSpanInScope(Runnable runnable) {
		return signal -> {
			Context context = signal.getContext();
			withSpanInScope(context, runnable);
		};
	}

	/**
	 * Wraps a runnable with a span.
	 * @param context - Reactor context that contains the {@link Span}
	 * @param runnable - lambda to execute within the tracing context
	 */
	public static void withSpanInScope(Context context, Runnable runnable) {
		Tracer tracer = context.get(Tracer.class);
		Span span = spanOrNew(context);
		try (Scope scope = tracer.withSpan(span)) {
			runnable.run();
		}
	}

	/**
	 * Wraps a callable with a span.
	 * @param context - Reactor context that contains the {@link Span}
	 * @param callable - lambda to execute within the tracing context
	 * @param <T> callable's return type
	 * @return value from the callable
	 */
	public static <T> T withSpanInScope(Context context, Callable<T> callable) {
		Tracer tracer = context.get(Tracer.class);
		Span span = spanOrNew(context);
		return withContext(callable, tracer, span);
	}

	private static Span spanOrNew(Context context) {
		Tracer tracer = context.get(Tracer.class);
		if (!context.hasKey(Span.class)) {
			if (log.isDebugEnabled()) {
				log.debug("No trace context found, will create a new span");
			}
			return tracer.spanBuilder("").startSpan();
		}
		return context.get(Span.class);
	}

	/**
	 * Wraps a runnable with a span.
	 * @param tracer - tracer bean
	 * @param exchange - server web exchange that can contain the {@link Span} in its
	 * attribute
	 * @param runnable - lambda to execute within the tracer context
	 */
	public static void withSpanInScope(Tracer tracer, ServerWebExchange exchange, Runnable runnable) {
		Span span = spanFromExchangeOrNew(tracer, exchange);
		try (Scope scope = tracer.withSpan(span)) {
			runnable.run();
		}
	}

	/**
	 * Wraps a callable with a span.
	 * @param tracer - tracer bean
	 * @param exchange - server web exchange that can contain the {@link Span} in its
	 * attribute
	 * @param callable - lambda to execute within the tracer context
	 * @param <T> callable's return type
	 * @return value from the callable
	 */
	public static <T> T withSpanInScope(Tracer tracer, ServerWebExchange exchange, Callable<T> callable) {
		Span span = spanFromExchangeOrNew(tracer, exchange);
		return withContext(callable, tracer, span);
	}

	/**
	 * Returns the current trace context.
	 * @param exchange - server web exchange that can contain the {@link Span} in its
	 * attribute
	 * @return current trace context or {@code null} if it's not present
	 */
	public static Span currentTracer(ServerWebExchange exchange) {
		return exchange.getAttribute(Span.class.getName());
	}

	/**
	 * Returns the current trace context.
	 * @param context - Reactor context that can contain the {@link Span}
	 * @return current trace context or {@code null} if it's not present
	 */
	public static Tracer currentTracer(Context context) {
		return context.getOrDefault(Tracer.class, null);
	}

	/**
	 * Returns the current trace context.
	 * @param signal - Reactor signal that can contain the {@link Span} in its context
	 * @return current trace context or {@code null} if it's not present
	 */
	public static Tracer currentTracer(Signal signal) {
		return currentTracer(signal.getContext());
	}

	private static <T> T withContext(Callable<T> callable, Tracer tracer, Span span) {
		try (Scope scope = tracer.withSpan(span)) {
			try {
				return callable.call();
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private static Span spanFromExchangeOrNew(Tracer tracer, ServerWebExchange exchange) {
		Span span = exchange.getAttribute(Span.class.getName());
		if (span == null) {
			if (log.isDebugEnabled()) {
				log.debug("No trace context found, will create a new span");
			}
			span = tracer.spanBuilder("").startSpan();
		}
		return span;
	}

}
