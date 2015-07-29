package org.springframework.cloud.sleuth.slf4j;

import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.cloud.sleuth.SpanIdentifiers;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.event.SpanStartedEvent;
import org.springframework.context.ApplicationListener;

/**
 * @author Spencer Gibb
 */
@Slf4j
public class Slf4jSpanStartedListener implements ApplicationListener<SpanStartedEvent> {

	@Override
	public void onApplicationEvent(SpanStartedEvent event) {
		SpanIdentifiers span = event.getSpan();
		MDC.put(Trace.SPAN_ID_NAME, span.getSpanId());
		MDC.put(Trace.TRACE_ID_NAME, span.getTraceId());
		//TODO: what log level?
		log.info("Starting span: {}", span);
	}
}
