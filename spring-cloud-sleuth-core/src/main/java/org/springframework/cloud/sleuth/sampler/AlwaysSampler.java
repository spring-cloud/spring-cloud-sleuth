package org.springframework.cloud.sleuth.sampler;

import org.springframework.cloud.sleuth.Sampler;

/**
 * @author Spencer Gibb
 */
public class AlwaysSampler implements Sampler<Object> {
	@Override
	public boolean next(Object info) {
		return true;
	}
}
