package org.springframework.cloud.sleuth.slf4j;

import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanStartListener;
import org.springframework.cloud.sleuth.Trace;

/**
 * @author Spencer Gibb
 */
@Slf4j
public class Slf4jSpanStartListener implements SpanStartListener {

	@Override
	public void startSpan(Span span) {
		//TODO: what log level?
		log.info("Starting span with id: [{}]", span.getSpanId());
		MDC.put(Trace.SPAN_ID_NAME, span.getSpanId());
	}
}
