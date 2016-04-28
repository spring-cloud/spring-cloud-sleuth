package org.springframework.cloud.sleuth.sampler;

import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;

/**
 * Sampler that based on the given percentage rate will allow sampling.
 * <p>
 *
 * A couple of assumptions have to take place in order for the algorithm to work properly:
 * <p>
 *
 * <ul>
 *     <li>We're taking the trace id into consideration for sampling to be consistent</li>
 *     <li>We apply the Zipkin algorithm to define whether we should sample or not (we're comparing against threshold)
 *     - https://github.com/openzipkin/zipkin-java/blob/master/zipkin/src/main/java/zipkin/Sampler.java</li>
 * </ul>
 *
 * The value provided from sampler configuration in terms of percentage is an estimation. It might occur that amount
 * of data sampled differs from the provided percentage.
 *
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 * @since 1.0.0
 */
public class PercentageBasedSampler implements Sampler {

	private final SamplerProperties configuration;

	public PercentageBasedSampler(SamplerProperties configuration) {
		this.configuration = configuration;
	}

	@Override
	public boolean isSampled(Span currentSpan) {
		long threshold = (long) (Long.MAX_VALUE * this.configuration.getPercentage()); // drops fractional percentage.
		if (currentSpan == null || threshold == 0L) {
			return false;
		}
		long traceId = currentSpan.getTraceId();
		long t = traceId == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(traceId);
		return t <= threshold;
	}

}
