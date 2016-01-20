package org.springframework.cloud.sleuth.sampler;

import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAccessor;

/**
 * Sampler that based on the given percentage rate will allow sampling.
 *
 * A couple of assumptions have to take place in order for the algorithm to work properly:
 *
 * <ul>
 *     <li>We're taking the TraceID into consideration for sampling to be consistent</li>
 *     <li>We apply the Zipkin algorithm to define whether we should sample or not (we're comparing against thresholdg) - https://github.com/openzipkin/zipkin-java/blob/master/zipkin/src/main/java/zipkin/Sampler.java</li>
 * </ul>
 *
 * The value provided from SamplerConfiguration in terms of percentage is an estimation. It might occur that amount
 * of data sampled differs from the provided percentage.
 *
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 */
public class PercentageBasedSampler implements Sampler {

	private final SamplerConfiguration configuration;
	private final SpanAccessor spanAccessor;

	public PercentageBasedSampler(SamplerConfiguration configuration, SpanAccessor spanAccessor) {
		this.configuration = configuration;
		this.spanAccessor = spanAccessor;
	}

	@Override
	public boolean isSampled() {
		Span currentSpan = this.spanAccessor.getCurrentSpan();
		long threshold = Math.abs(Long.MAX_VALUE * (int) (this.configuration.getPercentage() * 100)); // drops fractional percentage.
		if (currentSpan == null || threshold == 0L) {
			return false;
		}
		long traceId = currentSpan.getTraceId();
		Long mod = Math.abs(traceId % 100);
		return mod.compareTo(threshold) <= 0;
	}

}
