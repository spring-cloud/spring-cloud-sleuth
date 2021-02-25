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

package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.concurrent.atomic.AtomicBoolean;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.context.Context;

/**
 * A trace representation of the {@link Subscriber}.
 *
 * @deprecated use {@link ScopePassingSpanSubscriber} instead
 * @param <T> - return type of the subscriber
 * @author Stephane Maldini
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Deprecated
final class SpanSubscriber<T> extends AtomicBoolean implements SpanSubscription<T> {

	private static final Logger log = Loggers.getLogger(SpanSubscriber.class);

	private final Span span;

	private final TraceContext parent;

	private final Subscriber<? super T> subscriber;

	private final Context context;

	private final Tracer tracer;

	private final CurrentTraceContext currentTraceContext;

	private Subscription s;

	SpanSubscriber(Subscriber<? super T> subscriber, Context ctx, Tracing tracing,
			String name) {
		this.subscriber = subscriber;
		this.tracer = tracing.tracer();
		this.currentTraceContext = tracing.currentTraceContext();
		TraceContext parent = ctx.getOrDefault(TraceContext.class, null);
		if (parent == null) {
			parent = currentTraceContext.get();
		}
		if (log.isTraceEnabled()) {
			log.trace("Span from context [{}]", parent);
		}
		this.parent = parent;
		if (log.isTraceEnabled()) {
			log.trace("Stored context parent span [{}]", this.parent);
		}
		this.span = parent != null ? this.tracer.newChild(parent).name(name)
				: this.tracer.newTrace().name(name);
		if (log.isTraceEnabled()) {
			log.trace("Created span [{}], with name [{}]", this.span, name);
		}
		this.context = ctx.put(TraceContext.class, this.span.context());
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		if (log.isTraceEnabled()) {
			log.trace("On subscribe");
		}
		this.s = subscription;
		try (Scope ws = this.currentTraceContext.maybeScope(this.span.context())) {
			if (log.isTraceEnabled()) {
				log.trace("On subscribe - span continued");
			}
			this.subscriber.onSubscribe(this);
		}
	}

	@Override
	public void request(long n) {
		if (log.isTraceEnabled()) {
			log.trace("Request");
		}
		try (Scope ws = this.currentTraceContext.maybeScope(this.span.context())) {
			if (log.isTraceEnabled()) {
				log.trace("Request - continued");
			}
			this.s.request(n);
			// no additional cleaning is required cause we operate on scopes
			if (log.isTraceEnabled()) {
				log.trace("Request after cleaning. Current span [{}]",
						this.span.context());
			}
		}
	}

	@Override
	public void cancel() {
		try {
			if (log.isTraceEnabled()) {
				log.trace("Cancel");
			}
			this.s.cancel();
		}
		finally {
			cleanup();
		}
	}

	@Override
	public void onNext(T o) {
		this.subscriber.onNext(o);
	}

	@Override
	public void onError(Throwable throwable) {
		try {
			this.subscriber.onError(throwable);
		}
		finally {
			cleanup();
		}
	}

	@Override
	public void onComplete() {
		try {
			this.subscriber.onComplete();
		}
		finally {
			cleanup();
		}
	}

	void cleanup() {
		if (compareAndSet(false, true)) {
			if (log.isTraceEnabled()) {
				log.trace("Cleaning up");
			}
			this.span.finish();
			if (log.isTraceEnabled()) {
				log.trace("Span closed");
			}
			if (this.parent != null) {
				this.tracer.toSpan(parent).finish(); // TODO: why are we closing this?
				if (log.isTraceEnabled()) {
					log.trace("Closed parent span");
				}
			}
		}
	}

	@Override
	public Context currentContext() {
		return this.context;
	}

}
