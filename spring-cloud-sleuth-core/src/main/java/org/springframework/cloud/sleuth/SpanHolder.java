package org.springframework.cloud.sleuth;

import lombok.extern.apachecommons.CommonsLog;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class SpanHolder {
	private static final ThreadLocal<Span> currentSpan = new ThreadLocal<>();

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
