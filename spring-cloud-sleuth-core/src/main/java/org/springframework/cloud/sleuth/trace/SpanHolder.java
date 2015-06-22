package org.springframework.cloud.sleuth.trace;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Spencer Gibb
 */
public class SpanHolder {
	private static final ThreadLocal<Span> currentSpan = new ThreadLocal<>();
	private static final Log log = LogFactory.getLog(SpanHolder.class);

	public static Span getCurrentSpan() {
		return currentSpan.get();
	}

	public static void setCurrentSpan(Span span) {
		if (log.isTraceEnabled()) {
			log.trace("Setting current span " + span);
		}
		currentSpan.set(span);
	}
}
