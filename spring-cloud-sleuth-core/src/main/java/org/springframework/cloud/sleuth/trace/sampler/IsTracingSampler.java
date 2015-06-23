package org.springframework.cloud.sleuth.trace.sampler;

import org.springframework.cloud.sleuth.trace.Sampler;
import org.springframework.cloud.sleuth.trace.Trace;

/**
 * @author Spencer Gibb
 */
public class IsTracingSampler implements Sampler<Object> {
	private final Trace trace;

	public IsTracingSampler(Trace trace) {
		this.trace = trace;
	}

	@Override
	public boolean next(Object info) {
		return this.trace.isTracing();
	}
}
