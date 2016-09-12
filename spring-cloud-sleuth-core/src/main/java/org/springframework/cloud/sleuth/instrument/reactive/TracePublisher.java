/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.reactive;

import java.util.Objects;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

/**
 * @author Marcin Grzejszczak
 */
public class TracePublisher<T> implements Publisher<T> {

	final Publisher<T> delegate;
	final Tracer tracer;

	/**
	 * @param publisher
	 * @param tracer
	 * @param <T>
	 * @return
	 */
	public static <T> TracePublisher<T> from(Publisher<T> publisher, Tracer tracer) {
		return new TracePublisher<>(publisher, tracer);
	}

	protected TracePublisher(Publisher<T> delegate, Tracer tracer) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.tracer = Objects.requireNonNull(tracer, "tracer");
	}

	@Override public void subscribe(Subscriber<? super T> s) {
		this.delegate.subscribe(new TraceSubscriber<>(s, this.tracer));
	}

	final class TraceSubscriber<T> implements Subscriber<T> {
		final Span parent;
		final Tracer tracer;
		final Subscriber<T> actual;

		Span current;

		public TraceSubscriber(Subscriber<T> s, Tracer tracer) {
			this.actual = Objects.requireNonNull(s, "s");
			this.tracer = Objects.requireNonNull(tracer, "tracer");
			this.parent = this.tracer.getCurrentSpan();
		}

		@Override public void onSubscribe(Subscription s) {
			Span span = null;
			if (this.parent == null) {
				span = this.tracer.createSpan("");
			}
			else {
				span = this.tracer.continueSpan(this.parent);
			}
			this.current = span;
			this.actual.onSubscribe(s);
		}

		@Override public void onNext(T t) {
			this.actual.onNext(t);
		}

		@Override public void onError(Throwable t) {
			try {
				this.actual.onError(t);
			}
			finally {
				this.tracer.close(current);
			}
		}

		@Override public void onComplete() {
			try {
				this.actual.onComplete();
			}
			finally {
				this.tracer.close(current);
			}
		}
	}
}
