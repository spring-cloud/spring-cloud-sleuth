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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.reactivestreams.Subscription;
import reactor.util.context.Context;

/**
 * A lazy representation of the {@link SpanSubscription}
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
final class LazySpanSubscriber<T> extends AtomicBoolean implements SpanSubscription<T> {

	private final Supplier<SpanSubscription<T>> supplier;

	LazySpanSubscriber(Supplier<SpanSubscription<T>> supplier) {
		this.supplier = supplier;
	}

	@Override public void onSubscribe(Subscription subscription) {
		this.supplier.get().onSubscribe(subscription);
	}

	@Override public void request(long n) {
		this.supplier.get().request(n);
	}

	@Override public void cancel() {
		this.supplier.get().cancel();
	}

	@Override public void onNext(T o) {
		this.supplier.get().onNext(o);
	}

	@Override public void onError(Throwable throwable) {
		this.supplier.get().onError(throwable);
	}

	@Override public void onComplete() {
		this.supplier.get().onComplete();
	}

	@Override public Context currentContext() {
		return this.supplier.get().currentContext();
	}
}

