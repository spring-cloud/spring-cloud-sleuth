package org.springframework.cloud.sleuth.sampler;

import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.TraceContextHolder;

/**
 * @author Spencer Gibb
 */
public class IsTracingSampler implements Sampler<Object> {

	@Override
	public boolean next(Object info) {
		return TraceContextHolder.getCurrentSpan() != null;
	}
}
