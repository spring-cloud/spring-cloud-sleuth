package org.springframework.cloud.sleuth.trace;

/**
 * @author Spencer Gibb
 */
public class SpanHolder {
	private static final ThreadLocal<Span> currentSpan = new ThreadLocal<>();

	public Span getCurrentSpan() {
		return currentSpan.get();
	}

	public void setCurrentSpan(Span span) {
		currentSpan.set(span);
	}
}
