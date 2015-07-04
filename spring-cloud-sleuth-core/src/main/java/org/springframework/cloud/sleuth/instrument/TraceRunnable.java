package org.springframework.cloud.sleuth.instrument;

import lombok.Value;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;

/**
 * @author Spencer Gibb
 */
@Value
public class TraceRunnable extends TraceDelegate<Runnable> implements Runnable {

	public TraceRunnable(Trace trace, Runnable delagate) {
		super(trace, delagate);
	}

	public TraceRunnable(Trace trace, Runnable delagate, Span parent) {
		super(trace, delagate, parent);
	}

	public TraceRunnable(Trace trace, Runnable delagate, Span parent, String name) {
		super(trace, delagate, parent, name);
	}

	@Override
	public void run() {
		if (this.parent != null) {
			TraceScope scope = startSpan();

			try {
				this.delagate.run();
			}
			finally {
				scope.close();
			}
		}
		else {
			this.delagate.run();
		}
	}
}
