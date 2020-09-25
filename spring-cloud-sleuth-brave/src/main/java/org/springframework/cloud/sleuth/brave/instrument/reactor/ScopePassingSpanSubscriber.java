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

package org.springframework.cloud.sleuth.brave.instrument.reactor;

import javax.annotation.Nullable;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
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

	private final TraceContext parent;

	private Subscription s;

	ScopePassingSpanSubscriber(Subscriber<? super T> subscriber, Context ctx, CurrentTraceContext currentTraceContext,
			@Nullable TraceContext parent) {
		this.subscriber = subscriber;
		this.currentTraceContext = currentTraceContext;
		this.parent = parent;
		this.context = parent != null && !parent.equals(ctx.getOrDefault(TraceContext.class, null))
				? ctx.put(TraceContext.class, parent) : ctx;
		if (log.isTraceEnabled()) {
			log.trace("Parent span [" + parent + "], context [" + this.context + "]");
		}
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		this.s = subscription;
		try (Scope scope = this.currentTraceContext.maybeScope(this.parent)) {
			this.subscriber.onSubscribe(this);
		}
	}

	@Override
	public void request(long n) {
		try (Scope scope = this.currentTraceContext.maybeScope(this.parent)) {
			this.s.request(n);
		}
	}

	@Override
	public void cancel() {
		try (Scope scope = this.currentTraceContext.maybeScope(this.parent)) {
			this.s.cancel();
		}
	}

	@Override
	public void onNext(T o) {
		try (Scope scope = this.currentTraceContext.maybeScope(this.parent)) {
			this.subscriber.onNext(o);
		}
	}

	@Override
	public void onError(Throwable throwable) {
		try (Scope scope = this.currentTraceContext.maybeScope(this.parent)) {
			this.subscriber.onError(throwable);
		}
	}

	@Override
	public void onComplete() {
		try (Scope scope = this.currentTraceContext.maybeScope(this.parent)) {
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

	@Override
	public String toString() {
		return "ScopePassingSpanSubscriber{" + "subscriber=" + this.subscriber + ", parent=" + this.parent + "}";
	}

}
