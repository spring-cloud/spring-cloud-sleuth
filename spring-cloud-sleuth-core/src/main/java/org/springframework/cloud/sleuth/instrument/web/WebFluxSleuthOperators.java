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

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;
import reactor.util.context.Context;

/**
 * WebFlux operators that are capable to reuse tracing context from Reactor's Context.
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
	public static Consumer<Signal> withSpanInScope(SignalType signalType,
			Runnable runnable) {
		return signal -> {
			if (signalType != signal.getType()) {
				return;
			}
			withSpanInScope(runnable).accept(signal);
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
	 * @param context - Reactor context that contains the {@link TraceContext}
	 * @param runnable - lambda to execute within the tracing context
	 */
	public static void withSpanInScope(Context context, Runnable runnable) {
		CurrentTraceContext currentTraceContext = context.get(CurrentTraceContext.class);
		TraceContext traceContext = traceContextOrNew(context);
		try (CurrentTraceContext.Scope scope = currentTraceContext
				.maybeScope(traceContext)) {
			runnable.run();
		}
	}

	private static TraceContext traceContextOrNew(Context context) {
		Tracing tracing = context.get(Tracing.class);
		if (!context.hasKey(TraceContext.class)) {
			if (log.isDebugEnabled()) {
				log.debug("No trace context found, will create a new span");
			}
			return tracing.tracer().nextSpan().context();
		}
		return context.get(TraceContext.class);
	}

	/**
	 * Wraps a runnable with a span.
	 * @param context - Reactor context that contains the {@link TraceContext}
	 * @param callable - lambda to execute within the tracing context
	 * @param <T> callable's return type
	 * @return value from the callable
	 */
	public static <T> T withSpanInScope(Context context, Callable<T> callable) {
		CurrentTraceContext currentTraceContext = context.get(CurrentTraceContext.class);
		TraceContext traceContext = traceContextOrNew(context);
		try (CurrentTraceContext.Scope scope = currentTraceContext
				.maybeScope(traceContext)) {
			try {
				return callable.call();
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}
	}

}
