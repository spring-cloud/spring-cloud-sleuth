package org.springframework.cloud.sleuth.instrument;

import java.util.concurrent.Callable;

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
public class TraceCallable<V> extends TraceDelegate<Callable<V>> implements Callable<V> {

	public TraceCallable(Trace trace, Callable<V> delagate) {
		super(trace, delagate);
	}

	public TraceCallable(Trace trace, Callable<V> delegate, SpanIdentifiers parent) {
		super(trace, delegate, parent);
	}

	public TraceCallable(Trace trace, Callable<V> delegate, SpanIdentifiers parent, String name) {
		super(trace, delegate, parent, name);
	}

	@Override
	public V call() throws Exception {
		if (this.getParent() != null) {
			TraceScope scope = startSpan();

			try {
				return this.getDelegate().call();
			}
			finally {
				scope.close();
			}

		}
		else {
			return this.getDelegate().call();
		}
	}

}
