package org.springframework.cloud.sleuth.zipkin;

import lombok.Data;
import lombok.extern.apachecommons.CommonsLog;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanIdentifiers;
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
		preTrace(event.getParent(), event.getSpan());
	}

	@EventListener
	public void stop(SpanStoppedEvent event) {
		postTrace(event.getParent(), event.getSpan());
	}

	protected void preTrace(SpanIdentifiers parent, Span span) {
		String spanName = span.getName();
		if (span.getTraceId() != null && span.getSpanId() != null) {
			log.debug("Received span information as part of request.");
			this.serverTracer.setStateCurrentTrace(hash(span.getTraceId()),
					getSpanId(span), getSpanId(parent), spanName);
		}
		else {
			log.debug("Received no span state.");
			this.serverTracer.setStateUnknown(spanName);
		}
		this.serverTracer.setServerReceived();
	}

	private Long getSpanId(SpanIdentifiers span) {
		return span == null ? null : hash(span.getSpanId());
	}

	protected String getSpanName(Span context, TraceData traceData) {
		return context.getName();
	}

	protected void postTrace(SpanIdentifiers parent, Span span) {
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

	@Data
	private static class TraceData {
		private Long traceId;
		private Long spanId;
		private Long parentSpanId;
		private String spanName;
	}

	private static long hash(String string) {
		long h = 1125899906842597L;
		int len = string.length();

		for (int i = 0; i < len; i++) {
			h = 31 * h + string.charAt(i);
		}
		return h;
	}

}
