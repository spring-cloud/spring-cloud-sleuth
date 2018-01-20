package org.springframework.cloud.sleuth.sampler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties related to sampling
 *
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 * @since 1.0.0
 */
@ConfigurationProperties("spring.sleuth.sampler")
public class SamplerProperties {

	/**
	 * Percentage of requests that should be sampled. E.g. 1.0 - 100% requests should be
	 * sampled. The precision is whole-numbers only (i.e. there's no support for 0.1% of
	 * the traces).
	 */
	private float probability = 0.1f;

	public float getProbability() {
		return this.probability;
	}

	public void setProbability(float probability) {
		this.probability = probability;
	}
}
