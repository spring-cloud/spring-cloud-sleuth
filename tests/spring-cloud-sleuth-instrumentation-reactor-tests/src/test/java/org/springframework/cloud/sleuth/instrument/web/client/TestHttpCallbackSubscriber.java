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
import java.util.function.BiConsumer;

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

/**
 * {@link #subscribe} is made for reactor-netty and WebFlux client requests used in tests.
 * This does more assertions than normal, to ensure instrumentation isn't redundantly
 * signalling, or missing signals.
 *
 * <p>
 * The implementation forwards signals to the supplied {@linkplain BiConsumer callback},
 * enforcing assumptions about a non-empty, {@link Mono} subscription.
 */
final class TestHttpCallbackSubscriber implements CoreSubscriber<Integer> {

	static void subscribe(Mono<Integer> mono, BiConsumer<Integer, Throwable> callback) {
		mono.subscribe(new TestHttpCallbackSubscriber(callback));
	}

	final BiConsumer<Integer, Throwable> callback;

	final AtomicReference<Subscription> ref = new AtomicReference<>();

	private TestHttpCallbackSubscriber(BiConsumer<Integer, Throwable> callback) {
		this.callback = callback;
	}

	@Override
	public void onSubscribe(Subscription s) {
		if (Operators.validate(ref.getAndSet(s), s)) {
			s.request(Long.MAX_VALUE);
		}
		else {
			// We don't intentionally call subscribe() multiple times in our tests. If we
			// reach here, possibly instrumentation is redundantly subscribing.
			callback.accept(null, new AssertionError("onSubscribe() called twice!"));
		}
	}

	@Override
	public void onNext(Integer t) {
		if (ref.getAndSet(null) != null) {
			callback.accept(t, null);
		}
		else {
			// This is a Mono, which doesn't signal onNext() twice. If we reach here,
			// possibly instrumentation is signaling twice.
			callback.accept(null, new AssertionError("onNext() called twice!"));
		}
	}

	@Override
	public void onError(Throwable t) {
		if (ref.getAndSet(null) != null) {
			callback.accept(null, t);
		}
		else {
			// We don't expect onError() to signal twice. If we reach here, possibly
			// instrumentation is signaling twice or onSuccess() threw an exception.
			callback.accept(null, new AssertionError("onError() called twice: " + t, t));
		}
	}

	@Override
	public void onComplete() {
		if (ref.getAndSet(null) != null) {
			// Tests make a non-empty Mono subscription, which should not signal
			// onComplete() before onNext(). If we reach here, possibly instrumentation
			// is not signaling onNext() when it should.
			callback.accept(null,
					new AssertionError("onComplete() called before onNext!"));
		}
	}

	@Override
	public Context currentContext() {
		return Context.empty();
	}

}
