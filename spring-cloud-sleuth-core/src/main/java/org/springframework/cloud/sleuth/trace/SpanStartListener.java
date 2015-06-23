package org.springframework.cloud.sleuth.trace;

/**
 * @author Spencer Gibb
 */
//TODO: rename?
public interface SpanStartListener {
	void startSpan(Span span);
}
