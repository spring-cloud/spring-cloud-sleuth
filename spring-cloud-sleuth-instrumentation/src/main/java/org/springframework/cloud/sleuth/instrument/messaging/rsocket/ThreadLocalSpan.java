package org.springframework.cloud.sleuth.instrument.messaging.rsocket;

import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingDeque;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// Consider moving it out directly to Sleuth cause it's a copy from SI
class ThreadLocalSpan {

	private static final Log log = LogFactory.getLog(ThreadLocalSpan.class);

	final ThreadLocal<SpanAndScope> threadLocalSpan = new ThreadLocal<>();

	final LinkedBlockingDeque<SpanAndScope> spans = new LinkedBlockingDeque<>();

	void set(SpanAndScope spanAndScope) {
		SpanAndScope scope = this.threadLocalSpan.get();
		if (scope != null) {
			this.spans.addFirst(scope);
		}
		this.threadLocalSpan.set(spanAndScope);
	}

	SpanAndScope get() {
		return this.threadLocalSpan.get();
	}

	void remove() {
		this.threadLocalSpan.remove();
		if (this.spans.isEmpty()) {
			return;
		}
		try {
			SpanAndScope span = this.spans.removeFirst();
			if (log.isDebugEnabled()) {
				log.debug("Took span [" + span + "] from thread local");
			}
			this.threadLocalSpan.set(span);
		}
		catch (NoSuchElementException ex) {
			if (log.isTraceEnabled()) {
				log.trace("Failed to remove a span from the queue", ex);
			}
		}
	}

}
