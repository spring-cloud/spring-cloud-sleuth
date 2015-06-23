package org.springframework.cloud.sleuth.slf4j;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.sleuth.trace.Span;
import org.springframework.cloud.sleuth.trace.SpanStartListener;

/**
 * @author Spencer Gibb
 */
@Slf4j
public class Slf4jSpanStartListener implements SpanStartListener {
	//TODO: Where to put span id name?
	public static final String SPAN_ID_NAME = "Span-Id";

	@Override
	public void startSpan(Span span) {
		//TODO: what log level?
		log.info("Starting span with id: [{}]", span.getSpanId());
		MDC.put(SPAN_ID_NAME, span.getSpanId());
	}
}
