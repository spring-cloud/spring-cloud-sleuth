package org.springframework.cloud.sleuth.sampler;

import org.springframework.cloud.sleuth.Sampler;

/**
 * @author Spencer Gibb
 */
public class AlwaysSampler implements Sampler<Void> {
	@Override
	public boolean next(Void info) {
		return true;
	}
}
