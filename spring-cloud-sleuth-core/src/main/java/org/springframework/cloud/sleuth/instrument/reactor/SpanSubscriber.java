package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.concurrent.atomic.AtomicBoolean;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import reactor.core.CoreSubscriber;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.context.Context;

/**
 * A trace representation of the {@link Subscriber}
 *
 * @author Stephane Maldini
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
final class SpanSubscriber<T> extends AtomicBoolean implements Subscription,
		CoreSubscriber<T> {

	private static final Logger log = Loggers.getLogger(SpanSubscriber.class);

	private final Span span;
	private final Span rootSpan;
	private final Subscriber<? super T> subscriber;
	private final Context context;
	private final Tracer tracer;
	private Subscription s;

	SpanSubscriber(Subscriber<? super T> subscriber, Context ctx, Tracer tracer,
			String name) {
		this.subscriber = subscriber;
		this.tracer = tracer;
		Span root = ctx.getOrDefault(Span.class, tracer.getCurrentSpan());
		if (log.isTraceEnabled()) {
			log.trace("Span from context [{}]", root);
		}
		this.rootSpan = root;
		if (log.isTraceEnabled()) {
			log.trace("Stored context root span [{}]", this.rootSpan);
		}
		this.span = tracer.createSpan(name, root);
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
		this.tracer.continueSpan(this.span);
		if (log.isTraceEnabled()) {
			log.trace("On subscribe - span continued");
		}
		this.subscriber.onSubscribe(this);
	}

	@Override public void request(long n) {
		if (log.isTraceEnabled()) {
			log.trace("Request");
		}
		this.tracer.continueSpan(this.span);
		if (log.isTraceEnabled()) {
			log.trace("Request - continued");
		}
		this.s.request(n);
		// We're in the main thread so we don't want to pollute it with wrong spans
		// that's why we need to detach the current one and continue with its parent
		Span localRootSpan = this.span;
		while (localRootSpan != null) {
			if (this.rootSpan != null) {
				if (localRootSpan.getSpanId() != this.rootSpan.getSpanId() &&
						!isRootParentSpan(localRootSpan)) {
					localRootSpan = continueDetachedSpan(localRootSpan);
				} else {
					localRootSpan = null;
				}
			} else if (!isRootParentSpan(localRootSpan)) {
				localRootSpan = continueDetachedSpan(localRootSpan);
			} else {
				localRootSpan = null;
			}
		}
		if (log.isTraceEnabled()) {
			log.trace("Request after cleaning. Current span [{}]",
					this.tracer.getCurrentSpan());
		}
	}

	private boolean isRootParentSpan(Span localRootSpan) {
		return localRootSpan.getSpanId() == localRootSpan.getTraceId();
	}

	private Span continueDetachedSpan(Span localRootSpan) {
		if (log.isTraceEnabled()) {
			log.trace("Will detach span {}", localRootSpan);
		}
		Span detachedSpan = this.tracer.detach(localRootSpan);
		return this.tracer.continueSpan(detachedSpan);
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
			if (this.tracer.getCurrentSpan() != this.span) {
				if (log.isTraceEnabled()) {
					log.trace("Detaching span");
				}
				this.tracer.detach(this.tracer.getCurrentSpan());
				this.tracer.continueSpan(this.span);
				if (log.isTraceEnabled()) {
					log.trace("Continuing span");
				}
			}
			if (log.isTraceEnabled()) {
				log.trace("Closing span");
			}
			this.tracer.close(this.span);
			if (log.isTraceEnabled()) {
				log.trace("Span closed");
			}
			if (this.rootSpan != null) {
				this.tracer.continueSpan(this.rootSpan);
				this.tracer.close(this.rootSpan);
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