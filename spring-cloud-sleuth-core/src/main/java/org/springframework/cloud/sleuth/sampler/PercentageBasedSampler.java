package org.springframework.cloud.sleuth.sampler;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.cloud.sleuth.Sampler;

/**
 * Sampler that based on the given percentage rate will allow sampling.
 *
 * TODO: Think about better solution from thread-safety point
 *  this might be not 100% accurate
 */
public class PercentageBasedSampler implements Sampler<Void> {

	private final SamplerConfiguration samplerConfiguration;
	private final AtomicLong sucessfulSamples = new AtomicLong();
	private final AtomicLong rejectedSamples = new AtomicLong();

	public PercentageBasedSampler(SamplerConfiguration samplerConfiguration) {
		this.samplerConfiguration = samplerConfiguration;
	}

	@Override
	public boolean next(Void info) {
		long successful = sucessfulSamples.get();
		long rejected = rejectedSamples.get();
		double percentage = (successful + 1d) / (successful + 1d + rejected);
		boolean sample = percentage <= samplerConfiguration.getPercentage();
		if (sample) {
			sucessfulSamples.incrementAndGet();
		} else {
			rejectedSamples.incrementAndGet();
		}
		return sample;
	}
}
