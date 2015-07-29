package org.springframework.cloud.sleuth.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanIdentifiers;
import org.springframework.context.ApplicationEvent;

/**
 * @author Spencer Gibb
 */
@Data
@EqualsAndHashCode(callSuper=false)
@SuppressWarnings("serial")
public class SpanStartedEvent extends ApplicationEvent {

	private final SpanIdentifiers parent;
	private final Span span;

	public SpanStartedEvent(Object source, Span span) {
		this(source, null, span);
	}

	public SpanStartedEvent(Object source, SpanIdentifiers parent, Span span) {
		super(source);
		this.parent = parent;
		this.span = span;
	}
}
