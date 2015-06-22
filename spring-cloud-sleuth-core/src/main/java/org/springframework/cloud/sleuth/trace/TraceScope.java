package org.springframework.cloud.sleuth.trace;

import lombok.Data;
import lombok.extern.apachecommons.CommonsLog;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Spencer Gibb
 */
@Data
@CommonsLog
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
		if (detached) {
			error("Tried to detach trace span " + span + " but " +
					"it has already been detached.");
		}
		detached = true;

		Span cur = SpanHolder.getCurrentSpan();
		if (cur != span) {
			error("Tried to detach trace span " + span + " but " +
					"it is not the current span for the " +
					Thread.currentThread().getName() + " thread.  You have " +
					"probably forgotten to close or detach " + cur);
		} else {
			SpanHolder.setCurrentSpan(savedSpan);
		}
		return span;
	}

	@Override
	public void close() throws IOException {
		if (detached) {
			return;
		}
		detached = true;
		Span cur = SpanHolder.getCurrentSpan();
		if (cur != span) {
			error("Tried to close trace span " + span + " but " +
					"it is not the current span for the " +
					Thread.currentThread().getName() + " thread.  You have " +
					"probably forgotten to close or detach " + cur);
		} else {
			span.stop();
			SpanHolder.setCurrentSpan(savedSpan);
		}
	}

	private void error(String msg) {
		log.error(msg);
		throw new RuntimeException(msg);
	}
}
