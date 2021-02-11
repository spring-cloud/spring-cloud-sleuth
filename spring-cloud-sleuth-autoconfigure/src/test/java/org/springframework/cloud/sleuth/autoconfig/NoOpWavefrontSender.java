/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.autoconfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;

/**
 * A noop implementation. Does nothing.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class NoOpWavefrontSender implements WavefrontSender {

	@Override
	public String getClientId() {
		return null;
	}

	@Override
	public void flush() {

	}

	@Override
	public int getFailureCount() {
		return 0;
	}

	@Override
	public void sendDistribution(String name, List<Pair<Double, Integer>> centroids,
			Set<HistogramGranularity> histogramGranularities, Long timestamp, String source, Map<String, String> tags) {

	}

	@Override
	public void sendMetric(String name, double value, Long timestamp, String source, Map<String, String> tags) {

	}

	@Override
	public void sendFormattedMetric(String point) {

	}

	@Override
	public void sendSpan(String name, long startMillis, long durationMillis, String source, UUID traceId, UUID spanId,
			List<UUID> parents, List<UUID> followsFrom, List<Pair<String, String>> tags, List<SpanLog> spanLogs) {
	}

	@Override
	public void close() {

	}

}
