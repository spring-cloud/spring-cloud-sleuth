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

package org.springframework.cloud.sleuth.instrument.reactor;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.util.context.Context;

/**
 * A trace representation of the {@link Subscriber} that always continues a span.
 *
 * @param <T> subscription type
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
final class ScopePassingSpanSubscriber<T> implements SpanSubscription<T> {

	private static final Log log = LogFactory.getLog(ScopePassingSpanSubscriber.class);

	private final Subscriber<? super T> subscriber;

	private final Context context;

	private final Tracing tracing;
	private final CurrentTraceContext currentTraceContext;
	private final TraceContext traceContext;

	private Subscription s;

	ScopePassingSpanSubscriber(Subscriber<? super T> subscriber, Context ctx,
	                           Tracing tracing) {
		this.subscriber = subscriber;
		this.tracing = tracing;
		Tracer tracer = tracing.tracer();
		this.currentTraceContext = this.tracing.currentTraceContext();
		Span rootSpan = ctx != null ? ctx.getOrDefault(Span.class, tracer.currentSpan())
				: null;
		TraceContext rootTraceContext = ctx != null ? ctx.getOrDefault(TraceContext.class, this.currentTraceContext.get()) : null;
		this.traceContext = rootTraceContext;
		this.context = ctx != null && rootTraceContext != null ? ctx.put(TraceContext.class, rootTraceContext)
				: ctx != null ? ctx : Context.empty();
		if(rootTraceContext != null ) {
			this.context.put(Span.class, rootSpan);
		}
		if (log.isTraceEnabled()) {
			log.trace("Root traceContext [" + rootTraceContext + "], context [" + this.context + "]");
		}
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		this.s = subscription;
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
			this.subscriber.onSubscribe(this);
		}
	}

	@Override
	public void request(long n) {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
			this.s.request(n);
		}
	}

	@Override
	public void cancel() {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
			this.s.cancel();
		}

	}

	@Override
	public void onNext(T o) {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
			this.subscriber.onNext(o);
		}
	}

	@Override
	public void onError(Throwable throwable) {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
			this.subscriber.onError(throwable);
		}
	}

	@Override
	public void onComplete() {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
			this.subscriber.onComplete();
		}
	}

	@Override
	public Context currentContext() {
		return this.context;
	}

}
