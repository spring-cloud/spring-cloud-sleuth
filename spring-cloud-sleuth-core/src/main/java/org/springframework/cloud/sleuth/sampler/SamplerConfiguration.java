package org.springframework.cloud.sleuth.sampler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 */
@ConfigurationProperties("spring.sleuth.sampler")
public class SamplerConfiguration {

	/**
	 * Percentage of requests that should be sampled. E.g. 1.0 - 100% requests should be
	 * sampled. The precision is whole-numbers only (i.e. there's no support for 0.1% of
	 * the traces).
	 */
	private float percentage = 0.1f;

	public SamplerConfiguration() {
	}

	public float getPercentage() {
		return this.percentage;
	}

	public void setPercentage(float percentage) {
		this.percentage = percentage;
	}

	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof SamplerConfiguration))
			return false;
		final SamplerConfiguration other = (SamplerConfiguration) o;
		if (!other.canEqual((Object) this))
			return false;
		if (Float.compare(this.percentage, other.percentage) != 0)
			return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + Float.floatToIntBits(this.percentage);
		return result;
	}

	protected boolean canEqual(Object other) {
		return other instanceof SamplerConfiguration;
	}

	public String toString() {
		return "org.springframework.cloud.sleuth.sampler.SamplerConfiguration(percentage="
				+ this.percentage + ")";
	}
}
