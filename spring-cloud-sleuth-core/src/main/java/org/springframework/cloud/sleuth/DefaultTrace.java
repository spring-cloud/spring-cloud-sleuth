package org.springframework.cloud.sleuth;

import static org.springframework.cloud.sleuth.Utils.error;

import java.util.Collections;

import org.springframework.cloud.sleuth.event.SpanStartedEvent;
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
				.parents(Collections.singletonList(tinfo.getSpanId()))
				//TODO: when lombok plugin supports @Singular parent(tinfo.getSpanId()).
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
		return MilliSpan.builder().
				begin(System.currentTimeMillis()).
				name(childname).
				traceId(parent.getTraceId()).
				parents(Collections.singletonList(parent.getSpanId())).
				//TODO: when lombok plugin supports @Singular parent(parent.getSpanId()).
				spanId(idGenerator.create()).
				processId(parent.getProcessId()).
				build();
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
}
