package org.springframework.cloud.sleuth.zipkin;

import lombok.Data;
import lombok.extern.apachecommons.CommonsLog;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.event.SpanStartedEvent;
import org.springframework.cloud.sleuth.event.SpanStoppedEvent;
import org.springframework.context.event.EventListener;

import com.github.kristofa.brave.ServerTracer;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class ZipkinSpanListener {

	private final ServerTracer serverTracer;

	public ZipkinSpanListener(ServerTracer serverTracer) {
		this.serverTracer = serverTracer;
	}

	@EventListener
	public void start(SpanStartedEvent event) {
		preTrace(event.getSpan());
	}

	@EventListener
	public void start(SpanStoppedEvent event) {
		postTrace(event.getSpan());
	}

	protected void preTrace(Span context) {
		final TraceData traceData = getTraceData(context);
		this.serverTracer.clearCurrentSpan();

		if (Boolean.FALSE.equals(traceData.getShouldBeSampled())) {
			this.serverTracer.setStateNoTracing();
			log.debug("Received indication that we should NOT trace.");
		}
		else {
			final String spanName = getSpanName(context, traceData);
			if (traceData.getTraceId() != null && traceData.getSpanId() != null) {

				log.debug("Received span information as part of request.");
				this.serverTracer.setStateCurrentTrace(traceData.getTraceId(),
						traceData.getSpanId(), traceData.getParentSpanId(), spanName);
			}
			else {
				log.debug("Received no span state.");
				this.serverTracer.setStateUnknown(spanName);
			}
			this.serverTracer.setServerReceived();
		}
	}

	protected TraceData getTraceData(Span context) {
		TraceData trace = new TraceData();
		trace.setTraceId(hash(context.getTraceId()));
		trace.setSpanId(hash(context.getSpanId()));
		trace.setShouldBeSampled(true);
		trace.setSpanName(context.getName());
		if (!context.getParents().isEmpty()) {
			trace.setParentSpanId(hash(context.getParents().iterator().next()));
		}
		return trace;
	};

	protected String getSpanName(Span context, TraceData traceData) {
		return context.getName();
	}

	protected void postTrace(Span context) {
		// We can submit this in any case. When server state is not set or
		// we should not trace this request nothing will happen.
		log.debug("Sending server send.");
		try {
			this.serverTracer.setServerSend();
		}
		finally {
			this.serverTracer.clearCurrentSpan();
		}
	}

	protected ServerTracer getServerTracer() {
		return this.serverTracer;
	}

	private static long hash(String string) {
		long h = 1125899906842597L;
		int len = string.length();

		for (int i = 0; i < len; i++) {
			h = 31 * h + string.charAt(i);
		}
		return h;
	}

	@Data
	private static class TraceData {
		private Long traceId;
		private Long spanId;
		private Long parentSpanId;
		private Boolean shouldBeSampled;
		private String spanName;
	}
}
