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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;
import zipkin2.Callback;

/**
 * This is used to subscribe to reactor-netty and WebFlux client requests. This forwards
 * signals to the supplied {@link Callback}, enforcing assumptions about a non-empty,
 * {@link Mono} subscription.
 */
final class TestCallbackSubscriber<T> implements CoreSubscriber<T> {

	final Callback<Void> callback;

	final AtomicReference<Subscription> ref = new AtomicReference<>();

	TestCallbackSubscriber(Callback<Void> callback) {
		this.callback = callback;
	}

	@Override
	public void onSubscribe(Subscription s) {
		if (Operators.validate(ref.getAndSet(s), s)) {
			s.request(Long.MAX_VALUE);
		}
		else {
			// We don't intentionally call subscribe() multiple times!
			callback.onError(new AssertionError("onSubscribe() called twice!"));
		}
	}

	@Override
	public void onNext(T t) {
		if (ref.getAndSet(null) != null) {
			callback.onSuccess(null /* because Void */);
		}
		else {
			// This is a Mono, so we shouldn't receive onNext() twice!
			callback.onError(new AssertionError("onNext() called twice!"));
		}
	}

	@Override
	public void onError(Throwable t) {
		if (ref.getAndSet(null) != null) {
			callback.onError(t);
		}
	}

	@Override
	public void onComplete() {
		if (ref.getAndSet(null) != null) {
			// This is a non-empty subscription, so we should always receive an onNext()!
			callback.onError(new AssertionError("onComplete() called before onNext!"));
		}
	}

	@Override
	public Context currentContext() {
		return Context.empty();
	}

}
