package org.springframework.cloud.sleuth.trace.sampler;

import org.springframework.cloud.sleuth.trace.Sampler;

/**
 * @author Spencer Gibb
 */
public class AlwaysSampler implements Sampler<Object> {
	@Override
	public boolean next(Object info) {
		return true;
	}
}
