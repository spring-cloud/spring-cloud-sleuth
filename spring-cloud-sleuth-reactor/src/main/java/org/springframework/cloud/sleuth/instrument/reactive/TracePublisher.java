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
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Trace representation of the {@link Publisher}. When the {@code subscribe}
 * method is called the we're wrapping the {@link Subscriber} in a trace representation.
 * The trace representation is thread safe thus we can fully control the
 * span creation / closing process.
 *
 * @author Marcin Grzejszczak
 * @author Stephane Maldini
 * @since 1.0.9
 */
public class TracePublisher<T> implements Publisher<T> {

	private final Publisher<T> delegate;
	private final Tracer tracer;
	private final TraceKeys traceKeys;

	/**
	 * Helper static function to create a {@link TracePublisher}
	 */
	public static <T> TracePublisher<T> from(Publisher<T> publisher, Tracer tracer, TraceKeys traceKeys) {
		return new TracePublisher<>(publisher, tracer, traceKeys);
	}

	protected TracePublisher(Publisher<T> delegate, Tracer tracer, TraceKeys traceKeys) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.tracer = Objects.requireNonNull(tracer, "tracer");
		this.traceKeys = Objects.requireNonNull(traceKeys, "traceKeys");
	}

	@Override public void subscribe(Subscriber<? super T> s) {
		this.delegate.subscribe(new TraceSubscriber<>(s, this.tracer, this.traceKeys));
	}

	private class TraceSubscriber<V> implements Subscriber<V> {

		private static final String REACTIVE_COMPONENT = "reactive";

		private final Span parent;
		private final Tracer tracer;
		private final TraceKeys traceKeys;
		private final Subscriber<V> actual;

		private Span current;

		public TraceSubscriber(Subscriber<V> s, Tracer tracer, TraceKeys traceKeys) {
			this.actual = Objects.requireNonNull(s, "subscriber");
			this.tracer = Objects.requireNonNull(tracer, "tracer");
			this.traceKeys = Objects.requireNonNull(traceKeys, "traceKeys");
			this.parent = this.tracer.getCurrentSpan();
		}

		@Override
		public void onSubscribe(Subscription s) {
			Span span;
			if (this.parent == null) {
				span = this.tracer.createSpan(REACTIVE_COMPONENT);
			}
			else {
				span = this.tracer.continueSpan(this.parent);
			}
			if (!span.tags().containsKey(Span.SPAN_LOCAL_COMPONENT_TAG_NAME)) {
				this.tracer.addTag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, REACTIVE_COMPONENT);
			}
			this.tracer.addTag(this.traceKeys.getAsync().getPrefix()
					+ this.traceKeys.getAsync().getThreadNameKey(), Thread.currentThread().getName());
			this.current = span;
			this.actual.onSubscribe(s);
		}

		@Override
		public void onNext(V v) {
			this.actual.onNext(v);
		}

		@Override
		public void onError(Throwable t) {
			try {
				this.actual.onError(t);
			} finally {
				this.tracer.close(this.current);
			}
		}

		@Override
		public void onComplete() {
			try {
				this.actual.onComplete();
			} finally {
				this.tracer.close(this.current);
			}
		}
	}
}
