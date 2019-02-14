/*
 * Copyright 2013-2019 the original author or authors.
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

import javax.annotation.Nullable;

import brave.Span;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.Scannable;
import reactor.util.context.Context;

/**
 * A trace representation of the {@link Subscriber} that always continues a span.
 *
 * @param <T> subscription type
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
final class ScopePassingSpanSubscriber<T> implements SpanSubscription<T>, Scannable {

	private static final Log log = LogFactory.getLog(ScopePassingSpanSubscriber.class);

	private final Subscriber<? super T> subscriber;

	private final Context context;

	private final CurrentTraceContext currentTraceContext;

	private final TraceContext traceContext;

	private Subscription s;

	ScopePassingSpanSubscriber(Subscriber<? super T> subscriber, Context ctx,
			Tracing tracing, @Nullable Span root) {
		this.subscriber = subscriber;
		this.currentTraceContext = tracing.currentTraceContext();

		this.traceContext = root == null ? null : root.context();
		this.context = ctx != null && root != null ? ctx.put(Span.class, root)
				: ctx != null ? ctx : Context.empty();
		if (log.isTraceEnabled()) {
			log.trace("Root span [" + root + "], context [" + this.context + "]");
		}
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		this.s = subscription;
		try (CurrentTraceContext.Scope scope = this.currentTraceContext
				.maybeScope(this.traceContext)) {
			if (log.isTraceEnabled()) {
				log.trace("OnSubscribe");
			}
			this.subscriber.onSubscribe(this);
		}
	}

	@Override
	public void request(long n) {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext
				.maybeScope(this.traceContext)) {
			if (log.isTraceEnabled()) {
				log.trace("Request");
			}
			this.s.request(n);
		}
	}

	@Override
	public void cancel() {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext
				.maybeScope(this.traceContext)) {
			if (log.isTraceEnabled()) {
				log.trace("Cancel");
			}
			this.s.cancel();
		}

	}

	@Override
	public void onNext(T o) {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext
				.maybeScope(this.traceContext)) {
			if (log.isTraceEnabled()) {
				log.trace("OnNext");
			}
			this.subscriber.onNext(o);
		}
	}

	@Override
	public void onError(Throwable throwable) {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext
				.maybeScope(this.traceContext)) {
			if (log.isTraceEnabled()) {
				log.trace("OnError");
			}
			this.subscriber.onError(throwable);
		}
	}

	@Override
	public void onComplete() {
		try (CurrentTraceContext.Scope scope = this.currentTraceContext
				.maybeScope(this.traceContext)) {
			if (log.isTraceEnabled()) {
				log.trace("OnComplete");
			}
			this.subscriber.onComplete();
		}
	}

	@Override
	public Context currentContext() {
		return this.context;
	}

	@Override
	public Object scanUnsafe(Attr key) {
		if (key == Attr.PARENT) {
			return this.s;
		}
		else {
			return key == Attr.ACTUAL ? this.subscriber : null;
		}
	}

}
