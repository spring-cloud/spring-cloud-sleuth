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
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Propagate TraceContext instead of Span and calls maybeScope instead of withSpanInScope.
 * The purpose of this class is to reduce calls newScope and remove unused code.
 */
final class ScopePassingTraceContextSubscriber<T> extends AtomicBoolean implements SpanSubscription<T> {

	private static final Log log = LogFactory.getLog(ScopePassingTraceContextSubscriber.class);

	private final Subscriber<? super T> subscriber;
	private final Context context;
	private final Tracing tracing;
	private final CurrentTraceContext currentTraceContext;
	private final TraceContext root;
	private Subscription s;

	ScopePassingTraceContextSubscriber(Subscriber<? super T> subscriber, Context ctx, Tracing tracing) {
		this.subscriber = subscriber;
		this.tracing = tracing;
		this.currentTraceContext = this.tracing.currentTraceContext();
		TraceContext root = ctx != null ? ctx.getOrDefault(TraceContext.class, this.currentTraceContext.get()) : null;
		this.root = root;
		this.context = ctx != null && root != null ? ctx.put(Span.class, root) :
				ctx != null ? ctx : Context.empty();
		if (log.isTraceEnabled()) {
			log.trace("Root traceContext [" + root + "], context [" + this.context + "]");
		}
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		this.s = subscription;
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.root)) {
			this.subscriber.onSubscribe(this);
		}
	}

	@Override
	public void request(long n) {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.root)) {
			this.s.request(n);
		}
	}

	@Override
	public void cancel() {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.root)) {
			this.s.cancel();
		}

	}

	@Override
	public void onNext(T o) {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.root)) {
			this.subscriber.onNext(o);
		}
	}

	@Override
	public void onError(Throwable throwable) {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.root)) {
			this.subscriber.onError(throwable);
		}
	}

	@Override
	public void onComplete() {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.root)) {
			this.subscriber.onComplete();
		}
	}

	@Override
	public Context currentContext() {
		return this.context;
	}
}
