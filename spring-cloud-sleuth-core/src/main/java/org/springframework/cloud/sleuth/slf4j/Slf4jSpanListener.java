package org.springframework.cloud.sleuth.slf4j;

import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.event.SpanStartedEvent;
import org.springframework.cloud.sleuth.event.SpanStoppedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * @author Spencer Gibb
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class Slf4jSpanListener {

	@EventListener(SpanStartedEvent.class)
	public void start(SpanStartedEvent event) {
		Span span = event.getSpan();
		MDC.put(Trace.SPAN_ID_NAME, span.getSpanId());
		MDC.put(Trace.TRACE_ID_NAME, span.getTraceId());
		//TODO: what log level?
		log.info("Starting span: {}", span);
		if (event.getParent()!=null) {
			log.info("With parent: {}", event.getParent());
		}
	}

	@EventListener(SpanStoppedEvent.class)
	public void stop(SpanStoppedEvent event) {
		//TODO: what should this log level be?
		log.info("Stopped span: {}", event.getSpan());
		if (event.getParent()!=null) {
			log.info("With parent: {}", event.getParent());
		}
		MDC.remove(SPAN_ID_NAME);
		MDC.remove(TRACE_ID_NAME);
	}

}
