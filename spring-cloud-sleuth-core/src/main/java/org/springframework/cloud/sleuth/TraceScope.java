package org.springframework.cloud.sleuth;

import java.io.Closeable;

import lombok.Data;
import lombok.SneakyThrows;

/**
 * @author Spencer Gibb
 */
@Data
public class TraceScope implements Closeable {

	private final Trace trace;

	/**
	 * the span for this scope
	 */
	private final Span span;

	/**
	 * the span that was "current" before this scope was entered
	 */
	private final Span savedSpan;

	private boolean detached = false;

	public TraceScope(Trace trace, Span span, Span savedSpan) {
		this.trace = trace;
		this.span = span;
		this.savedSpan = savedSpan;
	}

	/**
	 * Remove this span as the current thread, but don't stop it yet or
	 * send it for collection. This is useful if the span object is then
	 * passed to another thread for use with Trace.continueTrace().
	 *
	 * @return the same Span object
	 */
	public Span detach() {
		if (detached) {
			Utils.error("Tried to detach trace span " + span + " but " +
					"it has already been detached.");
		}
		detached = true;

		Span cur = TraceContextHolder.getCurrentSpan();
		if (cur != span) {
			Utils.error("Tried to detach trace span " + span + " but " +
					"it is not the current span for the " +
					Thread.currentThread().getName() + " thread.  You have " +
					"probably forgotten to close or detach " + cur);
		} else {
			TraceContextHolder.setCurrentSpan(savedSpan);
		}
		return span;
	}

	@Override
	@SneakyThrows
	public void close() {
		if (detached) {
			return;
		}
		detached = true;
		Span cur = TraceContextHolder.getCurrentSpan();
		if (cur != span) {
			Utils.error("Tried to close trace span " + span + " but " +
					"it is not the current span for the " +
					Thread.currentThread().getName() + " thread.  You have " +
					"probably forgotten to close or detach " + cur);
		} else {
			span.stop();
			//TODO: use ApplicationEvents here?
			trace.deliver(span);
			TraceContextHolder.setCurrentSpan(savedSpan);
		}
	}

}
