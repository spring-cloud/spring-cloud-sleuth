package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.concurrent.atomic.AtomicBoolean;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContextOrSamplingFlags;
import reactor.core.CoreSubscriber;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.context.Context;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A trace representation of the {@link Subscriber}
 *
 * @author Stephane Maldini
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
final class SpanSubscriber<T> extends AtomicBoolean implements Subscription,
		CoreSubscriber<T> {

	private static final Logger log = Loggers.getLogger(
			SpanSubscriber.class);

	private final Span span;
	private final Span rootSpan;
	private final Subscriber<? super T> subscriber;
	private final Context context;
	private final Tracer tracer;
	private Subscription s;

	SpanSubscriber(Subscriber<? super T> subscriber, Context ctx, Tracing tracing,
			String name) {
		this.subscriber = subscriber;
		this.tracer = tracing.tracer();
		Span root = ctx.getOrDefault(Span.class, this.tracer.currentSpan());
		if (log.isTraceEnabled()) {
			log.trace("Span from context [{}]", root);
		}
		this.rootSpan = root;
		if (log.isTraceEnabled()) {
			log.trace("Stored context root span [{}]", this.rootSpan);
		}
		this.span = root != null ?
				this.tracer.nextSpan(TraceContextOrSamplingFlags.create(root.context()))
						.name(name) : this.tracer.nextSpan().name(name);
		if (log.isTraceEnabled()) {
			log.trace("Created span [{}], with name [{}]", this.span, name);
		}
		this.context = ctx.put(Span.class, this.span);
	}

	@Override public void onSubscribe(Subscription subscription) {
		if (log.isTraceEnabled()) {
			log.trace("On subscribe");
		}
		this.s = subscription;
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(this.span)) {
			if (log.isTraceEnabled()) {
				log.trace("On subscribe - span continued");
			}
			this.subscriber.onSubscribe(this);
		}
	}

	@Override public void request(long n) {
		if (log.isTraceEnabled()) {
			log.trace("Request");
		}
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(this.span)) {
			if (log.isTraceEnabled()) {
				log.trace("Request - continued");
			}
			this.s.request(n);
			// no additional cleaning is required cause we operate on scopes
			if (log.isTraceEnabled()) {
				log.trace("Request after cleaning. Current span [{}]",
						this.tracer.currentSpan());
			}
		}
	}

	@Override public void cancel() {
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

	@Override public void onNext(T o) {
		this.subscriber.onNext(o);
	}

	@Override public void onError(Throwable throwable) {
		try {
			this.subscriber.onError(throwable);
		}
		finally {
			cleanup();
		}
	}

	@Override public void onComplete() {
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
			Tracer.SpanInScope ws = null;
			if (this.tracer.currentSpan() != this.span) {
				if (log.isTraceEnabled()) {
					log.trace("Detaching span");
				}
				ws = this.tracer.withSpanInScope(this.span);
				if (log.isTraceEnabled()) {
					log.trace("Continuing span");
				}
			}
			if (log.isTraceEnabled()) {
				log.trace("Closing span");
			}
			this.span.finish();
			if (ws != null) {
				ws.close();
			}
			if (log.isTraceEnabled()) {
				log.trace("Span closed");
			}
			if (this.rootSpan != null) {
				this.rootSpan.finish();
				if (log.isTraceEnabled()) {
					log.trace("Closed root span");
				}
			}
		}
	}

	@Override public Context currentContext() {
		return this.context;
	}
}