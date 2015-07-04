package org.springframework.cloud.sleuth.slf4j;

import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;

import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.cloud.sleuth.event.SpanStoppedEvent;
import org.springframework.context.ApplicationListener;

/**
 * @author Spencer Gibb
 */
@Slf4j
public class Slf4jSpanStoppedListener implements ApplicationListener<SpanStoppedEvent> {
	@Override
	public void onApplicationEvent(SpanStoppedEvent event) {
		//TODO: what should this log level be?
		log.info("Received span: {}", event.getSpan());
		MDC.remove(SPAN_ID_NAME);
		MDC.remove(TRACE_ID_NAME);
	}
}
