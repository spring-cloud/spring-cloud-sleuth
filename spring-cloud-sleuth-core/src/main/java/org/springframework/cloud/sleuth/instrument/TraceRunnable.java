package org.springframework.cloud.sleuth.instrument;

import lombok.EqualsAndHashCode;
import lombok.Value;

import org.springframework.cloud.sleuth.SpanIdentifiers;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;

/**
 * @author Spencer Gibb
 */
@Value
@EqualsAndHashCode(callSuper=false)
public class TraceRunnable extends TraceDelegate<Runnable> implements Runnable {

	public TraceRunnable(Trace trace, Runnable delagate) {
		super(trace, delagate);
	}

	public TraceRunnable(Trace trace, Runnable delagate, SpanIdentifiers parent) {
		super(trace, delagate, parent);
	}

	public TraceRunnable(Trace trace, Runnable delagate, SpanIdentifiers parent, String name) {
		super(trace, delagate, parent, name);
	}

	@Override
	public void run() {
		if (this.getParent() != null) {
			TraceScope scope = startSpan();

			try {
				this.getDelegate().run();
			}
			finally {
				scope.close();
			}
		}
		else {
			this.getDelegate().run();
		}
	}
}
