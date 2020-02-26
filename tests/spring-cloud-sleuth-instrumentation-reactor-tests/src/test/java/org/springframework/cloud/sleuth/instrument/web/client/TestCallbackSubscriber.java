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
 * {@link #subscribe} is made for reactor-netty and WebFlux client requests. It forwards
 * signals to the supplied {@link Callback}, enforcing assumptions about a non-empty,
 * {@link Mono} subscription.
 */
final class TestCallbackSubscriber<T> implements CoreSubscriber<T> {

	static <T> void subscribe(Mono<T> mono, Callback<Void> callback) {
		mono.subscribe(new TestCallbackSubscriber<>(callback));
	}

	final Callback<Void> callback;

	final AtomicReference<Subscription> ref = new AtomicReference<>();

	private TestCallbackSubscriber(Callback<Void> callback) {
		this.callback = callback;
	}

	@Override
	public void onSubscribe(Subscription s) {
		if (Operators.validate(ref.getAndSet(s), s)) {
			s.request(Long.MAX_VALUE);
		}
		else {
			// We don't intentionally call subscribe() multiple times in our tests. If we
			// reach here, possibly the instrumentation is redundantly subscribing.
			callback.onError(new AssertionError("onSubscribe() called twice!"));
		}
	}

	@Override
	public void onNext(T t) {
		if (ref.getAndSet(null) != null) {
			callback.onSuccess(null /* because Void */);
		}
		else {
			// This is a Mono, which doesn't signal onNext() twice. If we reach here,
			// possibly the instrumentation is signaling twice.
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
			// Tests make a non-empty Mono subscription, which should not signal
			// onComplete() before onNext(). If we reach here, possibly the
			// instrumentation is not signaling onNext() when it should.
			callback.onError(new AssertionError("onComplete() called before onNext!"));
		}
	}

	@Override
	public Context currentContext() {
		return Context.empty();
	}

}
