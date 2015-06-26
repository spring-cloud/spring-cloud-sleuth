package org.springframework.cloud.sleuth.event;

import lombok.Value;
import org.springframework.cloud.sleuth.Span;
import org.springframework.context.ApplicationEvent;

/**
 * @author Spencer Gibb
 */
@Value
public class SpanStartedEvent extends ApplicationEvent {

	private final Span span;

	public SpanStartedEvent(Object source, Span span) {
		super(source);
		this.span = span;
	}
}
