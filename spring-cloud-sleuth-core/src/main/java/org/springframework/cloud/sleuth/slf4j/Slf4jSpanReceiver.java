package org.springframework.cloud.sleuth.slf4j;

import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReceiver;

import javax.annotation.PostConstruct;
import java.io.IOException;

/**
 * @author Spencer Gibb
 */
@Slf4j
public class Slf4jSpanReceiver implements SpanReceiver {
	@Override
	public void receiveSpan(Span span) {
		//TODO: what should this log level be?
		log.info("Received span {}", span);
		MDC.remove(SPAN_ID_NAME);
	}

	@PostConstruct
	@Override
	public void close() throws IOException {
	}
}
