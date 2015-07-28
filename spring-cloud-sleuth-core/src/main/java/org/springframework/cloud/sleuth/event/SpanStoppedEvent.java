package org.springframework.cloud.sleuth.event;

import lombok.EqualsAndHashCode;
import lombok.Value;

import org.springframework.cloud.sleuth.Span;
import org.springframework.context.ApplicationEvent;

/**
 * @author Spencer Gibb
 */
@Value
@EqualsAndHashCode(callSuper=false)
@SuppressWarnings("serial")
public class SpanStoppedEvent extends ApplicationEvent {

	private final Span span;

	public SpanStoppedEvent(Object source, Span span) {
		super(source);
		this.span = span;
	}
}
