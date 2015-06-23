package org.springframework.cloud.sleuth.trace;

import static org.springframework.cloud.sleuth.trace.Utils.error;

import org.springframework.cloud.sleuth.IdGenerator;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Spencer Gibb
 */
public class DefaultTrace implements Trace {

	private final Sampler<?> defaultSampler;

	private final IdGenerator idGenerator;

	private final Collection<SpanReceiver> spanReceivers;

	public DefaultTrace(Sampler<?> defaultSampler, IdGenerator idGenerator,
			Collection<SpanReceiver> spanReceivers) {
		this.defaultSampler = defaultSampler;
		this.idGenerator = idGenerator;
		this.spanReceivers = spanReceivers;
	}

	@Override
	public TraceScope startSpan(String description) {
		return this.startSpan(description, defaultSampler);
	}

	@Override
	public TraceScope startSpan(String description, TraceInfo tinfo) {
		if (tinfo == null) return continueSpan(null);
		MilliSpan span = MilliSpan.builder()
				.begin(System.currentTimeMillis())
				.description(description)
				.traceId(tinfo.getTraceId())
				.spanId(idGenerator.create())
				.parents(Collections.singletonList(tinfo.getSpanId()))
				//TODO: when lombok plugin supports @Singular parent(tinfo.getSpanId()).
				.build();
		return continueSpan(span);
	}

	@Override
	public TraceScope startSpan(String description, Span parent) {
		if (parent == null) {
			return startSpan(description);
		}
		Span currentSpan = getCurrentSpan();
		if ((currentSpan != null) && (currentSpan != parent)) {
			error("HTrace client error: thread " +
					Thread.currentThread().getName() + " tried to start a new Span " +
					"with parent " + parent.toString() + ", but there is already a " +
					"currentSpan " + currentSpan);
		}
		return continueSpan(createChild(parent, description));
	}

	@Override
	public <T> TraceScope startSpan(String description, Sampler<T> s) {
		return startSpan(description, s, null);
	}

	@Override
	public <T> TraceScope startSpan(String description, Sampler<T> s, T info) {
		Span span = null;
		if (isTracing() || s.next(info)) {
			span = createNew(description);
		}
		return continueSpan(span);
	}

	protected Span createNew(String description) {
		Span parent = getCurrentSpan();
		if (parent == null) {
			return MilliSpan.builder()
					.begin(System.currentTimeMillis())
					.description(description)
					.traceId(idGenerator.create())
					.spanId(idGenerator.create())
					.build();
		} else {
			return createChild(parent, description);
		}
	}

	protected Span createChild(Span parent, String childDescription) {
		return MilliSpan.builder().
				begin(System.currentTimeMillis()).
				description(childDescription).
				traceId(parent.getTraceId()).
				parents(Collections.singletonList(parent.getSpanId())).
				//TODO: when lombok plugin supports @Singular parent(parent.getSpanId()).
				spanId(idGenerator.create()).
				processId(parent.getProcessId()).
				build();
	}

	@Override
	public TraceScope continueSpan(Span span) {
		// Return an empty TraceScope that does nothing on close
		if (span == null) return NullScope.INSTANCE;
		Span oldSpan = getCurrentSpan();
		SpanHolder.setCurrentSpan(span);
		return new TraceScope(this, span, oldSpan);
	}

	protected Span getCurrentSpan() {
		return SpanHolder.getCurrentSpan();
	}

	@Override
	public void addKVAnnotation(String key, String value) {
		Span s = getCurrentSpan();
		if (s != null) {
			s.addKVAnnotation(key, value);
		}
	}

	@Override
	public boolean isTracing() {
		return getCurrentSpan() != null;
	}

	@Override
	public void deliver(Span span) {
		for (SpanReceiver receiver : spanReceivers) {
			receiver.receiveSpan(span);
		}
	}
}
