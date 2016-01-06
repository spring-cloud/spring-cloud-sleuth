package org.springframework.cloud.sleuth.sampler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 */
@ConfigurationProperties("spring.sleuth.sampler")
@Data
public class SamplerConfiguration {

	/**
	 * Percentage of requests that should be sampled.
	 * E.g.
	 * <ul>
	 * <li> 1.0 - 100% requests should be sampled </li>
	 * <li> 0.8 - 80% of requests should be sampled </li>
	 * <li> 0.0 - 0% requests should be sampled </li>
	 * </ul>
	 *
	 * The precision is whole-numbers only. We don't support 0.1% of the trace rate.
	 */
	private float percentage = 0.1f;
}
