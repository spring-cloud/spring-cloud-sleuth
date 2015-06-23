package org.springframework.cloud.sleuth.trace.sampler;

import org.springframework.cloud.sleuth.trace.Sampler;
import org.springframework.cloud.sleuth.trace.SpanHolder;

/**
 * @author Spencer Gibb
 */
public class IsTracingSampler implements Sampler<Object> {

	@Override
	public boolean next(Object info) {
		return SpanHolder.getCurrentSpan() != null;
	}
}
