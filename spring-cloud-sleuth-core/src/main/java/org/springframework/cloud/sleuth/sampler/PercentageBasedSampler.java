package org.springframework.cloud.sleuth.sampler;

import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;

/**
 * This sampler is appropriate for low-traffic instrumentation (ex servers that each receive <100K
 * requests), or those who do not provision random trace ids. It not appropriate for collectors as
 * the sampling decision isn't idempotent (consistent based on trace id).
 *
 * <h3>Implementation</h3>
 *
 * <p>Taken from <a href="https://github.com/openzipkin/zipkin-java/blob/traceid-sampler/zipkin/src/main/java/zipkin/CountingTraceIdSampler.java">Zipkin project</a></p>
 *
 * <p>This counts to see how many out of 100 traces should be retained. This means that it is
 * accurate in units of 100 traces.
 *
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 * @since 1.0.0
 */
public class PercentageBasedSampler implements Sampler {

	private final int outOf100;

	private int i = 0;
	private boolean skipping = false;

	public PercentageBasedSampler(SamplerProperties configuration) {
		this.outOf100 = (int) (configuration.getPercentage() * 100.0f);;
	}

	@Override
	public boolean isSampled(Span currentSpan) {
		if (this.outOf100 == 0 || currentSpan == null) {
			return false;
		} else if (this.outOf100 == 100) {
			return true;
		}
		synchronized (this) {
			boolean result = !this.skipping;
			this.i = this.i + 1;
			if (this.i == this.outOf100) {
				this.skipping = true;
			} else if (i == 100) {
				this.i = 0;
				this.skipping = false;
			}
			return result;
		}
	}

}
