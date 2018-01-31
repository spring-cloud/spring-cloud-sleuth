/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.sampler;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import brave.sampler.Sampler;

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
public class ProbabilityBasedSampler extends Sampler {

	private final AtomicInteger counter = new AtomicInteger(0);
	private final BitSet sampleDecisions;
	private final SamplerProperties configuration;

	public ProbabilityBasedSampler(SamplerProperties configuration) {
		int outOf100 = (int) (configuration.getProbability() * 100.0f);
		this.sampleDecisions = randomBitSet(100, outOf100, new Random());
		this.configuration = configuration;
	}

	@Override
	public boolean isSampled(long traceId) {
		if (this.configuration.getProbability() == 0) {
			return false;
		} else if (this.configuration.getProbability() == 1.0f) {
			return true;
		}
		synchronized (this) {
			final int i = this.counter.getAndIncrement();
			boolean result = this.sampleDecisions.get(i);
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
}
