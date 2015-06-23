package org.springframework.cloud.sleuth.trace;

/**
 * @author Spencer Gibb
 */
/**
 * Singleton instance representing an empty {@link TraceScope}.
 */
public final class NullScope extends TraceScope {

	public static final TraceScope INSTANCE = new NullScope();

	private NullScope() {
		super(null, null, null);
	}

	@Override
	public Span detach() {
		return null;
	}

	@Override
	public void close() {
		return;
	}

	@Override
	public String toString() {
		return "NullScope";
	}
}
