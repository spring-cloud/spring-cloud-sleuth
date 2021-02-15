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

package org.springframework.cloud.sleuth.autoconfig.wavefront;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.java_sdk.com.google.common.collect.Iterators;
import com.wavefront.java_sdk.com.google.common.collect.Sets;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.entities.tracing.SpanLog;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.util.StringUtils;

import static com.wavefront.internal.SpanDerivedMetricsUtils.TRACING_DERIVED_PREFIX;
import static com.wavefront.internal.SpanDerivedMetricsUtils.reportHeartbeats;
import static com.wavefront.internal.SpanDerivedMetricsUtils.reportWavefrontGeneratedData;
import static com.wavefront.sdk.common.Constants.APPLICATION_TAG_KEY;
import static com.wavefront.sdk.common.Constants.CLUSTER_TAG_KEY;
import static com.wavefront.sdk.common.Constants.COMPONENT_TAG_KEY;
import static com.wavefront.sdk.common.Constants.DEBUG_TAG_KEY;
import static com.wavefront.sdk.common.Constants.ERROR_TAG_KEY;
import static com.wavefront.sdk.common.Constants.NULL_TAG_VAL;
import static com.wavefront.sdk.common.Constants.SERVICE_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SHARD_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SOURCE_KEY;
import static com.wavefront.sdk.common.Constants.SPAN_LOG_KEY;

/**
 * This converts a span recorded by Brave and invokes {@link WavefrontSender#sendSpan}.
 *
 * <p>
 * This uses a combination of conversion approaches from Wavefront projects:
 * <ul>
 * <li>https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java</li>
 * <li>https://github.com/wavefrontHQ/wavefront-proxy</li>
 * </ul>
 *
 * <p>
 * On conflict, we make a comment and prefer wavefront-opentracing-sdk-java. The rationale
 * is wavefront-opentracing-sdk-java uses the same {@link WavefrontSender#sendSpan}
 * library, so it is easier to reason with. This policy can be revisited by future
 * maintainers.
 *
 * <p>
 * <em>Note:</em>UUID conversions follow the same conventions used in practice in
 * Wavefront. Ex.
 * https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/6babf2ff95daa37452e1e8c35ae54b58b6abb50f/src/main/java/com/wavefront/opentracing/propagation/JaegerWavefrontPropagator.java#L191-L204
 * While in practice this is not a problem, it is worth mentioning that this convention
 * will only only result in RFC 4122 timestamp (version 1) format by accident. In other
 * words, don't call {@link UUID#timestamp()} on UUIDs converted here, or in other
 * Wavefront code, as it might throw.
 *
 * @since 3.1.0
 */
public final class WavefrontSleuthSpanHandler implements Runnable, Closeable {

	private static final Log LOG = LogFactory.getLog(WavefrontSleuthSpanHandler.class);

	// https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L114-L114
	private static final String DEFAULT_SPAN_NAME = "defaultOperation";

	private final static String DEFAULT_SOURCE = "wavefront-spring-boot";

	private final static String WAVEFRONT_GENERATED_COMPONENT = "wavefront-generated";

	private static final int LONG_BYTES = Long.SIZE / Byte.SIZE;

	private static final int BYTE_BASE16 = 2;

	private static final int LONG_BASE16 = BYTE_BASE16 * LONG_BYTES;

	private static final int TRACE_ID_HEX_SIZE = 2 * LONG_BASE16;

	private static final String ALPHABET = "0123456789abcdef";

	private static final int ASCII_CHARACTERS = 128;

	private static final byte[] DECODING = buildDecodingArray();

	final LinkedBlockingQueue<Pair<TraceContext, FinishedSpan>> spanBuffer;

	final WavefrontSender wavefrontSender;

	final WavefrontInternalReporter wfInternalReporter;

	final Set<String> traceDerivedCustomTagKeys;

	final Counter spansDropped;

	final Counter spansReceived;

	final Counter reportErrors;

	final Thread sendingThread;

	private volatile boolean stop = false;

	private final Set<Pair<Map<String, String>, String>> discoveredHeartbeatMetrics;

	private final ScheduledExecutorService heartbeatMetricsScheduledExecutorService;

	final String source;

	final List<Pair<String, String>> defaultTags;

	final Set<String> defaultTagKeys;

	final ApplicationTags applicationTags;

	WavefrontSleuthSpanHandler(int maxQueueSize, WavefrontSender wavefrontSender, MeterRegistry meterRegistry,
			String source, ApplicationTags applicationTags, Set<String> redMetricsCustomTagKeys) {
		this.wavefrontSender = wavefrontSender;
		this.applicationTags = applicationTags;
		this.discoveredHeartbeatMetrics = Sets.newConcurrentHashSet();

		this.heartbeatMetricsScheduledExecutorService = Executors.newScheduledThreadPool(1,
				new NamedThreadFactory("sleuth-heart-beater").setDaemon(true));

		// Emit Heartbeats Metrics every 1 min.
		heartbeatMetricsScheduledExecutorService.scheduleAtFixedRate(() -> {
			try {
				reportHeartbeats(wavefrontSender, discoveredHeartbeatMetrics, WAVEFRONT_GENERATED_COMPONENT);
			}
			catch (IOException e) {
				LOG.warn("Cannot report heartbeat metric to wavefront");
			}
		}, 1, 60, TimeUnit.SECONDS);

		this.traceDerivedCustomTagKeys = new HashSet<>(redMetricsCustomTagKeys);

		// Start the reporter
		wfInternalReporter = new WavefrontInternalReporter.Builder().prefixedWith(TRACING_DERIVED_PREFIX)
				.withSource(DEFAULT_SOURCE).reportMinuteDistribution().build(wavefrontSender);
		wfInternalReporter.start(1, TimeUnit.MINUTES);

		this.source = source;
		this.defaultTags = createDefaultTags(applicationTags);
		this.defaultTagKeys = defaultTags.stream().map(p -> p._1).collect(Collectors.toSet());
		this.defaultTagKeys.add(SOURCE_KEY);

		this.spanBuffer = new LinkedBlockingQueue<>(maxQueueSize);

		// init internal metrics
		meterRegistry.gauge("reporter.queue.size", spanBuffer, sb -> (double) sb.size());
		meterRegistry.gauge("reporter.queue.remaining_capacity", spanBuffer, sb -> (double) sb.remainingCapacity());
		this.spansReceived = meterRegistry.counter("reporter.spans.received");
		this.spansDropped = meterRegistry.counter("reporter.spans.dropped");
		this.reportErrors = meterRegistry.counter("reporter.errors");

		this.sendingThread = new Thread(this, "wavefrontSpanReporter");
		this.sendingThread.setDaemon(true);
		this.sendingThread.start();
	}

	// Exact same behavior as WavefrontSpanReporter
	// https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/reporting/WavefrontSpanReporter.java#L163-L179
	public boolean end(TraceContext context, FinishedSpan span) {
		spansReceived.increment();
		if (!spanBuffer.offer(Pair.of(context, span))) {
			spansDropped.increment();
			if (LOG.isWarnEnabled()) {
				LOG.warn("Buffer full, dropping span: " + span);
				LOG.warn("Total spans dropped: " + spansDropped.count());
			}
		}
		return true; // regardless of error, other handlers should run
	}

	List<Pair<String, String>> getDefaultTags() {
		return Collections.unmodifiableList(this.defaultTags);
	}

	private String padLeftWithZeros(String string, int length) {
		if (string.length() >= length) {
			return string;
		}
		else {
			StringBuilder sb = new StringBuilder(length);
			for (int i = string.length(); i < length; i++) {
				sb.append('0');
			}

			return sb.append(string).toString();
		}
	}

	private void send(TraceContext context, FinishedSpan span) {
		String traceIdString = padLeftWithZeros(context.traceId(), TRACE_ID_HEX_SIZE);
		String traceIdHigh = traceIdString.substring(0, traceIdString.length() / 2);
		String traceIdLow = traceIdString.substring(traceIdString.length() / 2);
		UUID traceId = new UUID(longFromBase16String(traceIdHigh), longFromBase16String(traceIdLow));
		UUID spanId = new UUID(0L, longFromBase16String(context.spanId()));

		// NOTE: wavefront-opentracing-sdk-java and wavefront-proxy differ, but we prefer
		// the former.
		// https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/reporting/WavefrontSpanReporter.java#L187-L190
		// https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L248-L252
		List<UUID> parents = null;
		String parentId = context.parentId();
		if (StringUtils.hasText(parentId) && longFromBase16String(parentId) != 0L) {
			parents = Collections.singletonList(new UUID(0L, longFromBase16String(parentId)));
		}
		List<UUID> followsFrom = null;

		// https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L344-L345
		String name = span.getName();
		if (name == null) {
			name = DEFAULT_SPAN_NAME;
		}

		// Start and duration become 0L if unset. Any positive duration rounds up to 1
		// millis.
		long startMillis = span.getStartTimestamp() / 1000L;
		long finishMillis = span.getEndTimestamp() / 1000L;
		long durationMicros = span.getEndTimestamp() - span.getStartTimestamp();
		long durationMillis = startMillis != 0 && finishMillis != 0L ? Math.max(finishMillis - startMillis, 1L) : 0L;

		List<SpanLog> spanLogs = convertAnnotationsToSpanLogs(span);
		TagList tags = new TagList(defaultTagKeys, defaultTags, span);

		try {
			wavefrontSender.sendSpan(name, startMillis, durationMillis, source, traceId, spanId, parents, followsFrom,
					tags, spanLogs);
		}
		catch (IOException | RuntimeException t) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("error sending span " + context, t);
			}
		}

		// report stats irrespective of span sampling.
		if (wfInternalReporter != null) {
			// report converted metrics/histograms from the span
			try {
				discoveredHeartbeatMetrics.add(reportWavefrontGeneratedData(wfInternalReporter, name,
						applicationTags.getApplication(), applicationTags.getService(),
						applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster(),
						applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard(), source,
						tags.componentTagValue, tags.isError, durationMicros, traceDerivedCustomTagKeys, tags));
			}
			catch (RuntimeException t) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("error sending span RED metrics " + context, t);
				}
			}
		}
	}

	private static byte[] buildDecodingArray() {
		byte[] decoding = new byte[ASCII_CHARACTERS];
		Arrays.fill(decoding, (byte) -1);
		for (int i = 0; i < ALPHABET.length(); i++) {
			char c = ALPHABET.charAt(i);
			decoding[c] = (byte) i;
		}
		return decoding;
	}

	/**
	 * Returns the {@code long} value whose base16 representation is stored in the first
	 * 16 chars of {@code chars} starting from the {@code offset}.
	 * @param chars the base16 representation of the {@code long}.
	 */
	private static long longFromBase16String(CharSequence chars) {
		int offset = 0;
		return (decodeByte(chars.charAt(offset), chars.charAt(offset + 1)) & 0xFFL) << 56
				| (decodeByte(chars.charAt(offset + 2), chars.charAt(offset + 3)) & 0xFFL) << 48
				| (decodeByte(chars.charAt(offset + 4), chars.charAt(offset + 5)) & 0xFFL) << 40
				| (decodeByte(chars.charAt(offset + 6), chars.charAt(offset + 7)) & 0xFFL) << 32
				| (decodeByte(chars.charAt(offset + 8), chars.charAt(offset + 9)) & 0xFFL) << 24
				| (decodeByte(chars.charAt(offset + 10), chars.charAt(offset + 11)) & 0xFFL) << 16
				| (decodeByte(chars.charAt(offset + 12), chars.charAt(offset + 13)) & 0xFFL) << 8
				| (decodeByte(chars.charAt(offset + 14), chars.charAt(offset + 15)) & 0xFFL);
	}

	private static byte decodeByte(char hi, char lo) {
		int decoded = DECODING[hi] << 4 | DECODING[lo];
		return (byte) decoded;
	}

	@Override
	public void run() {
		while (!stop) {
			try {
				Pair<TraceContext, FinishedSpan> contextAndSpan = spanBuffer.take();
				send(contextAndSpan._1, contextAndSpan._2);
			}
			catch (InterruptedException ex) {
				if (LOG.isInfoEnabled()) {
					LOG.info("reporting thread interrupted");
				}
			}
			catch (Throwable ex) {
				LOG.warn("Error processing buffer", ex);
			}
		}
	}

	@Override
	public void close() {
		stop = true;
		try {
			// wait for 5 secs max
			sendingThread.join(5000);
			heartbeatMetricsScheduledExecutorService.shutdownNow();
		}
		catch (InterruptedException ex) {
			// no-op
		}
	}

	// https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L397-L402
	static List<SpanLog> convertAnnotationsToSpanLogs(FinishedSpan span) {
		int annotationCount = span.getEvents().size();
		if (annotationCount == 0) {
			return Collections.emptyList();
		}
		List<SpanLog> spanLogs = new ArrayList<>(annotationCount);
		for (int i = 0; i < annotationCount; i++) {
			Map.Entry<Long, String> entry = Iterators.get(span.getEvents().iterator(), i);
			long epochMicros = entry.getKey();
			String value = entry.getValue();
			spanLogs.add(new SpanLog(epochMicros, Collections.singletonMap("annotation", value)));
		}
		return spanLogs;
	}

	// https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/WavefrontTracer.java#L275-L280
	static List<Pair<String, String>> createDefaultTags(ApplicationTags applicationTags) {
		List<Pair<String, String>> result = new ArrayList<>();
		result.add(Pair.of(APPLICATION_TAG_KEY, applicationTags.getApplication()));
		result.add(Pair.of(SERVICE_TAG_KEY, applicationTags.getService()));
		result.add(Pair.of(CLUSTER_TAG_KEY,
				applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster()));
		result.add(
				Pair.of(SHARD_TAG_KEY, applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard()));
		if (applicationTags.getCustomTags() != null) {
			applicationTags.getCustomTags().forEach((k, v) -> result.add(Pair.of(k, v)));
		}
		return result;
	}

	/**
	 * Extracted for test isolation and as parsing otherwise implies multiple-returns or
	 * scanning later.
	 *
	 * <p>
	 * Ex. {@code SpanDerivedMetricsUtils#reportWavefrontGeneratedData} needs tags
	 * separately from the component tag and error status.
	 */
	static final class TagList extends ArrayList<Pair<String, String>> {

		String componentTagValue = NULL_TAG_VAL;

		boolean isError; // See explanation here:

		// https://github.com/openzipkin/brave/pull/1221

		TagList(Set<String> defaultTagKeys, List<Pair<String, String>> defaultTags, FinishedSpan span) {
			super(defaultTags.size() + span.getTags().size());
			// TODO: OTel doesn't have a notion of debug
			boolean debug = false;
			boolean hasAnnotations = span.getEvents().size() > 0;
			isError = span.getError() != null;

			int tagCount = span.getTags().size();
			addAll(defaultTags);
			for (int i = 0; i < tagCount; i++) {
				String tagKey = Iterators.get(span.getTags().keySet().iterator(), i);
				String tagValue = Iterators.get(span.getTags().values().iterator(), i);
				String key = tagKey;
				String value = tagValue;
				String lcKey = key.toLowerCase(Locale.ROOT);
				if (lcKey.equals(ERROR_TAG_KEY)) {
					isError = true;
					continue; // We later replace whatever the potentially empty value was
					// with "true"
				}
				if (value.isEmpty()) {
					continue;
				}
				if (defaultTagKeys.contains(lcKey)) {
					continue;
				}
				if (lcKey.equals(DEBUG_TAG_KEY)) {
					debug = true; // This tag is set out-of-band
					continue;
				}
				if (lcKey.equals(COMPONENT_TAG_KEY)) {
					componentTagValue = value;
				}
				add(Pair.of(key, value));
			}

			// Check for span.error() for uncaught exception in request mapping and add it
			// to Wavefront span tag
			if (isError) {
				add(Pair.of("error", "true"));
			}

			// https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L300-L303
			if (debug) {
				add(Pair.of(DEBUG_TAG_KEY, "true"));
			}

			// https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L254-L266
			if (span.getKind() != null) {
				String kind = span.getKind().toString().toLowerCase();
				add(Pair.of("span.kind", kind));
				if (hasAnnotations) {
					add(Pair.of("_spanSecondaryId", kind));
				}
			}

			// https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L329-L332
			if (hasAnnotations) {
				add(Pair.of(SPAN_LOG_KEY, "true"));
			}

			// https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L324-L327
			if (span.getLocalIp() != null) {
				add(Pair.of("ipv4", span.getLocalIp())); // NOTE: this could be IPv6!!
			}
		}

	}

}
