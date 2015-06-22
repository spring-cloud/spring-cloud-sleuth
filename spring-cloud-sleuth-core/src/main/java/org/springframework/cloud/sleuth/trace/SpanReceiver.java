package org.springframework.cloud.sleuth.trace;

import java.io.Closeable;

/**
 * The collector within a process that is the destination of Spans when a trace is running.
 */
public interface SpanReceiver extends Closeable {
	/**
	 * Called when a Span is stopped and can now be stored.
	 */
	public void receiveSpan(Span span);
}
