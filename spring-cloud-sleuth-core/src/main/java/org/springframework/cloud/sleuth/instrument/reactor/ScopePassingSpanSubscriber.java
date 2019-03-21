/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.concurrent.atomic.AtomicBoolean;

import brave.Span;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import reactor.util.context.Context;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A trace representation of the {@link Subscriber} that always
 * continues a span
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
final class ScopePassingSpanSubscriber<T> extends AtomicBoolean implements SpanSubscription<T> {

	private static final Log log = LogFactory.getLog(ScopePassingSpanSubscriber.class);

	private final TraceContext spanTraceContext;
	private final CurrentTraceContext currentTraceContext;
	private final Subscriber<? super T> subscriber;
	private final Context context;
	private Subscription s;

	ScopePassingSpanSubscriber(Subscriber<? super T> subscriber, Context ctx, Tracing tracing) {
		this.subscriber = subscriber;
		this.currentTraceContext = tracing.currentTraceContext();
		Span root = ctx != null ?
				ctx.getOrDefault(Span.class, tracing.tracer().currentSpan()) : null;
		this.spanTraceContext = root != null ? root.context() : null;
		this.context = ctx != null && root != null ? ctx.put(Span.class, root) :
				ctx != null ? ctx : Context.empty();
		if (log.isTraceEnabled()) {
			log.trace("Root span [" + root + "], context [" + this.context + "]");
		}
	}

	@Override public void onSubscribe(Subscription subscription) {
		this.s = subscription;
		try (CurrentTraceContext.Scope inScope = this.currentTraceContext.maybeScope(this.spanTraceContext)) {
			this.subscriber.onSubscribe(this);
		}
	}

	@Override public void request(long n) {
		try (CurrentTraceContext.Scope inScope = this.currentTraceContext.maybeScope(this.spanTraceContext)) {
			this.s.request(n);
		}
	}

	@Override public void cancel() {
		try (CurrentTraceContext.Scope inScope = this.currentTraceContext.maybeScope(this.spanTraceContext)) {
			this.s.cancel();
		}
	}

	@Override public void onNext(T o) {
		try (CurrentTraceContext.Scope inScope = this.currentTraceContext.maybeScope(this.spanTraceContext)) {
			this.subscriber.onNext(o);
		}
	}

	@Override public void onError(Throwable throwable) {
		this.subscriber.onError(throwable);
	}

	@Override public void onComplete() {
		this.subscriber.onComplete();
	}

	@Override public Context currentContext() {
		return this.context;
	}
}
