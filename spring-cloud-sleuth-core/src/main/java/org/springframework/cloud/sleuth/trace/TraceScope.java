package org.springframework.cloud.sleuth.trace;

import lombok.Data;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Spencer Gibb
 */
@Data
public class TraceScope implements Closeable {
	/**
	 * the span for this scope
	 */
	private final Span span;

	/**
	 * the span that was "current" before this scope was entered
	 */
	private final Span savedSpan;

	private boolean detached = false;

	/**
	 * Remove this span as the current thread, but don't stop it yet or
	 * send it for collection. This is useful if the span object is then
	 * passed to another thread for use with Trace.continueTrace().
	 *
	 * @return the same Span object
	 */
	public Span detach() {
		//TODO: implement detach
		return span;
	}

	@Override
	public void close() throws IOException {
		span.stop();
		//TODO: set savedSpan to currentSpan in SpanHolder
	}
}
