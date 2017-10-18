package org.springframework.cloud.sleuth.sampler;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.context.ApplicationListener;

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
public class PercentageBasedSampler implements Sampler,
		ApplicationListener<EnvironmentChangeEvent> {

	private final AtomicInteger counter = new AtomicInteger(0);
	private final AtomicReference<BitSet> sampleDecisions;
	private final SamplerProperties configuration;

	public PercentageBasedSampler(SamplerProperties configuration) {
		this.configuration = configuration;
		this.sampleDecisions = new AtomicReference<>(bitSet());
	}

	@Override
	public boolean isSampled(Span currentSpan) {
		if (this.configuration.getPercentage() == 0 || currentSpan == null) {
			return false;
		} else if (this.configuration.getPercentage() == 1.0f) {
			return true;
		}
		synchronized (this) {
			final int i = this.counter.getAndIncrement();
			boolean result = this.sampleDecisions.get().get(i);
			if (i == 99) {
				this.counter.set(0);
			}
			return result;
		}
	}

	/**
	 * Reservoir sampling algorithm borrowed from Stack Overflow.
	 *
	 * http://stackoverflow.com/questions/12817946/generate-a-random-bitset-with-n-1s
	 */
	static BitSet randomBitSet(int size, int cardinality, Random rnd) {
		BitSet result = new BitSet(size);
		int[] chosen = new int[cardinality];
		int i;
		for (i = 0; i < cardinality; ++i) {
			chosen[i] = i;
			result.set(i);
		}
		for (; i < size; ++i) {
			int j = rnd.nextInt(i + 1);
			if (j < cardinality) {
				result.clear(chosen[j]);
				result.set(i);
				chosen[j] = i;
			}
		}
		return result;
	}

	@Override
	public void onApplicationEvent(EnvironmentChangeEvent event) {
		if (event.getKeys().contains("spring.sleuth.sampler.percentage")) {
			this.sampleDecisions.set(bitSet());
		}
	}

	private BitSet bitSet() {
		int outOf100 = (int) (this.configuration.getPercentage() * 100.0f);
		return randomBitSet(100, outOf100, new Random());
	}
}
