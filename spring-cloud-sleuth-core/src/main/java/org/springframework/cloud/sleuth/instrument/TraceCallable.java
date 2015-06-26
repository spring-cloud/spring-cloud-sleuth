package org.springframework.cloud.sleuth.instrument;

import java.util.concurrent.Callable;

import lombok.Value;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;

/**
 * @author Spencer Gibb
 */
@Value
public class TraceCallable<V> extends TraceDelegate<Callable<V>> implements Callable<V> {

	public TraceCallable(Trace trace, Callable<V> delagate) {
		super(trace, delagate);
	}

	public TraceCallable(Trace trace, Callable<V> delagate, Span parent) {
		super(trace, delagate, parent);
	}

	public TraceCallable(Trace trace, Callable<V> delagate, Span parent, String name) {
		super(trace, delagate, parent, name);
	}

	@Override
	public V call() throws Exception {
		if (this.parent != null) {
			TraceScope scope = startSpan();

			try {
				return this.delagate.call();
			}
			finally {
				scope.close();
			}

		}
		else {
			return this.delagate.call();
		}
	}

}
