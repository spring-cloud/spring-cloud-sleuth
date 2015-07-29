package org.springframework.cloud.sleuth.sampler;

import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.TraceContextHolder;

/**
 * @author Spencer Gibb
 */
public class IsTracingSampler implements Sampler<Void> {

	@Override
	public boolean next(Void info) {
		return TraceContextHolder.getCurrentSpan() != null;
	}
}
