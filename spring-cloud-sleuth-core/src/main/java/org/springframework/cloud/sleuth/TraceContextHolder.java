package org.springframework.cloud.sleuth;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.core.NamedThreadLocal;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class TraceContextHolder {
	private static final ThreadLocal<Span> currentSpan = new NamedThreadLocal<>("Trace Context");

	public static Span getCurrentSpan() {
		return currentSpan.get();
	}

	public static void setCurrentSpan(Span span) {
		if (log.isTraceEnabled()) {
			log.trace("Setting current span " + span);
		}
		currentSpan.set(span);
	}

	public static boolean isTracing() {
		return currentSpan.get() != null;
	}
}
