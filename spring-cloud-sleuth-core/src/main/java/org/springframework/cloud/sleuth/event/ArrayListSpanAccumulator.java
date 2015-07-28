package org.springframework.cloud.sleuth.event;

import java.util.ArrayList;

import lombok.Value;

import org.springframework.cloud.sleuth.Span;
import org.springframework.context.ApplicationListener;

/**
 * @author Spencer Gibb
 */
@Value
public class ArrayListSpanAccumulator implements ApplicationListener<SpanStoppedEvent> {
	private final ArrayList<Span> spans = new ArrayList<>();

	@Override
	public void onApplicationEvent(SpanStoppedEvent event) {
		spans.add(event.getSpan());
	}
}
