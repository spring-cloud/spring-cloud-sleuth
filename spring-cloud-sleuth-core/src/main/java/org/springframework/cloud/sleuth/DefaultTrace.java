package org.springframework.cloud.sleuth;

import static org.springframework.cloud.sleuth.Utils.error;

import java.util.Collections;
import java.util.concurrent.Callable;

import org.springframework.cloud.sleuth.event.SpanStartedEvent;
import org.springframework.cloud.sleuth.instrument.TraceCallable;
import org.springframework.cloud.sleuth.instrument.TraceRunnable;
import org.springframework.context.ApplicationEventPublisher;

/**
 * @author Spencer Gibb
 */
public class DefaultTrace implements Trace {

	private final Sampler<?> defaultSampler;

	private final IdGenerator idGenerator;

	private final ApplicationEventPublisher publisher;

	public DefaultTrace(Sampler<?> defaultSampler, IdGenerator idGenerator,
			ApplicationEventPublisher publisher) {
		this.defaultSampler = defaultSampler;
		this.idGenerator = idGenerator;
		this.publisher = publisher;
	}

	@Override
	public TraceScope startSpan(String name) {
		return this.startSpan(name, defaultSampler);
	}

	@Override
	public TraceScope startSpan(String name, TraceInfo tinfo) {
		if (tinfo == null) return doStart(null);
		MilliSpan span = MilliSpan.builder()
				.begin(System.currentTimeMillis())
				.name(name)
				.traceId(tinfo.getTraceId())
				.spanId(idGenerator.create())
				.parent(tinfo.getSpanId())
				.build();
		return doStart(span);
	}

	@Override
	public TraceScope startSpan(String name, Span parent) {
		if (parent == null) {
			return startSpan(name);
		}
		Span currentSpan = getCurrentSpan();
		if ((currentSpan != null) && (currentSpan != parent)) {
			error("HTrace client error: thread " +
					Thread.currentThread().getName() + " tried to start a new Span " +
					"with parent " + parent.toString() + ", but there is already a " +
					"currentSpan " + currentSpan);
		}
		return doStart(createChild(parent, name));
	}

	@Override
	public <T> TraceScope startSpan(String name, Sampler<T> s) {
		return startSpan(name, s, null);
	}

	@Override
	public <T> TraceScope startSpan(String name, Sampler<T> s, T info) {
		Span span = null;
		if (TraceContextHolder.isTracing() || s.next(info)) {
			span = createNew(name);
		}
		return doStart(span);
	}

	protected Span createNew(String name) {
		Span parent = getCurrentSpan();
		if (parent == null) {
			return MilliSpan.builder()
					.begin(System.currentTimeMillis())
					.name(name)
					.traceId(idGenerator.create())
					.spanId(idGenerator.create())
					.build();
		} else {
			return createChild(parent, name);
		}
	}

	protected Span createChild(Span parent, String childname) {
		return MilliSpan.builder()
				.begin(System.currentTimeMillis())
				.name(childname)
				.traceId(parent.getTraceId())
				.parent(parent.getSpanId())
				.spanId(idGenerator.create())
				.processId(parent.getProcessId())
				.build();
	}

	protected TraceScope doStart(Span span) {
		if (span != null) {
			publisher.publishEvent(new SpanStartedEvent(this, span));
		}
		return continueSpan(span);
	}

	@Override
	public TraceScope continueSpan(Span span) {
		// Return an empty TraceScope that does nothing on close
		if (span == null) return NullScope.INSTANCE;
		Span oldSpan = getCurrentSpan();
		TraceContextHolder.setCurrentSpan(span);
		return new TraceScope(this.publisher, span, oldSpan);
	}

	protected Span getCurrentSpan() {
		return TraceContextHolder.getCurrentSpan();
	}

	@Override
	public void addKVAnnotation(String key, String value) {
		Span s = getCurrentSpan();
		if (s != null) {
			s.addKVAnnotation(key, value);
		}
	}

	/**
	 * Wrap the callable in a TraceCallable, if tracing.
	 *
	 * @return The callable provided, wrapped if tracing, 'callable' if not.
	 */
	@Override
	public <V> Callable<V> wrap(Callable<V> callable) {
		if (TraceContextHolder.isTracing()) {
			return new TraceCallable<>(this, callable, TraceContextHolder.getCurrentSpan());
		}
		return callable;
	}

	/**
	 * Wrap the runnable in a TraceRunnable, if tracing.
	 *
	 * @return The runnable provided, wrapped if tracing, 'runnable' if not.
	 */
	@Override
	public Runnable wrap(Runnable runnable) {
		if (TraceContextHolder.isTracing()) {
			return new TraceRunnable(this, runnable, TraceContextHolder.getCurrentSpan());
		}
		return runnable;
	}
}
