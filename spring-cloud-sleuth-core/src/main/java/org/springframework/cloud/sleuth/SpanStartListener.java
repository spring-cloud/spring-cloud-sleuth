package org.springframework.cloud.sleuth;

/**
 * @author Spencer Gibb
 */
//TODO: rename?
public interface SpanStartListener {
	void startSpan(Span span);
}
