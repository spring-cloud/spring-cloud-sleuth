/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.commons.util.IdUtils;
import org.springframework.cloud.sleuth.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAdjuster;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;

/**
 * Listener of Sleuth events. Reports to Zipkin via {@link ZipkinSpanReporter}.
 *
 * @author Spencer Gibb
 * @since 1.0.0
 */
public class ZipkinSpanListener implements SpanReporter {
	private static final List<String> ZIPKIN_START_EVENTS = Arrays.asList(
			Constants.CLIENT_RECV, Constants.SERVER_RECV
	);
	private static final List<String> RPC_EVENTS = Arrays.asList(
			Constants.CLIENT_RECV, Constants.CLIENT_SEND, Constants.SERVER_RECV, Constants.SERVER_SEND
	);

	private static final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory
			.getLog(ZipkinSpanListener.class);
	private static final Charset UTF_8 = Charset.forName("UTF-8");
	private static final byte[] UNKNOWN_BYTES = "unknown".getBytes(UTF_8);

	private final ZipkinSpanReporter reporter;
	private final Environment environment;
	private final List<SpanAdjuster> spanAdjusters;
	/**
	 * Endpoint is the visible IP address of this service, the port it is listening on and
	 * the service name from discovery.
	 */
	// Visible for testing
	final EndpointLocator endpointLocator;

	public ZipkinSpanListener(ZipkinSpanReporter reporter, EndpointLocator endpointLocator,
			Environment environment, List<SpanAdjuster> spanAdjusters) {
		this.reporter = reporter;
		this.endpointLocator = endpointLocator;
		this.environment = environment;
		this.spanAdjusters = spanAdjusters;
	}

	/**
	 * Converts a given Sleuth span to a Zipkin Span.
	 * <ul>
	 * <li>Set ids, etc
	 * <li>Create timeline annotations based on data from Span object.
	 * <li>Create binary annotations based on data from Span object.
	 * </ul>
	 *
	 * When logging {@link Constants#CLIENT_SEND}, instrumentation should also log the {@link Constants#SERVER_ADDR}
	 * Check <a href="https://github.com/openzipkin/zipkin-java/blob/master/zipkin/src/main/java/zipkin/Constants.java#L28">Zipkin code</a>
	 * for more information
	 */
	// Visible for testing
	zipkin.Span convert(Span span) {
		//TODO: Consider adding support for the debug flag (related to #496)
		Span convertedSpan = span;
		for (SpanAdjuster adjuster : this.spanAdjusters) {
			convertedSpan = adjuster.adjust(convertedSpan);
		}
		zipkin.Span.Builder zipkinSpan = zipkin.Span.builder();
		Endpoint endpoint = this.endpointLocator.local();
		processLogs(convertedSpan, zipkinSpan, endpoint);
		addZipkinAnnotations(zipkinSpan, convertedSpan, endpoint);
		addZipkinBinaryAnnotations(zipkinSpan, convertedSpan, endpoint);
		// In the RPC span model, the client owns the timestamp and duration of the span. If we
		// were propagated an id, we can assume that we shouldn't report timestamp or duration,
		// rather let the client do that. Worst case we were propagated an unreported ID and
		// Zipkin backfills timestamp and duration.
		if (!convertedSpan.isRemote()) {
			// don't report server-side timestamp on shared spans
			if (Boolean.TRUE.equals(convertedSpan.isShared())) {
				zipkinSpan.timestamp(null).duration(null);
			} else {
				zipkinSpan.timestamp(convertedSpan.getBegin() * 1000L);
				if (!convertedSpan.isRunning()) { // duration is authoritative, only write when the span stopped
					zipkinSpan.duration(calculateDurationInMicros(convertedSpan));
				}
			}
		}
		zipkinSpan.traceIdHigh(convertedSpan.getTraceIdHigh());
		zipkinSpan.traceId(convertedSpan.getTraceId());
		if (convertedSpan.getParents().size() > 0) {
			if (convertedSpan.getParents().size() > 1) {
				log.error("Zipkin doesn't support spans with multiple parents. Omitting "
						+ "other parents for " + convertedSpan);
			}
			zipkinSpan.parentId(convertedSpan.getParents().get(0));
		}
		zipkinSpan.id(convertedSpan.getSpanId());
		if (StringUtils.hasText(convertedSpan.getName())) {
			zipkinSpan.name(convertedSpan.getName());
		}
		return zipkinSpan.build();
	}

	private void ensureLocalComponent(Span span, zipkin.Span.Builder zipkinSpan, Endpoint localEndpoint) {
		if (span.tags().containsKey(Constants.LOCAL_COMPONENT)) {
			return;
		}
		byte[] processId = span.getProcessId() != null
				? span.getProcessId().toLowerCase().getBytes(UTF_8)
				: UNKNOWN_BYTES;
		BinaryAnnotation component = BinaryAnnotation.builder()
				.type(BinaryAnnotation.Type.STRING)
				.key("lc") // LOCAL_COMPONENT
				.value(processId)
				.endpoint(localEndpoint).build();
		zipkinSpan.addBinaryAnnotation(component);
	}

	private void ensureServerAddr(Span span, zipkin.Span.Builder zipkinSpan) {
		if (span.tags().containsKey(Span.SPAN_PEER_SERVICE_TAG_NAME)) {
			zipkinSpan.addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR,
					Endpoint.builder().serviceName(
							span.tags().get(Span.SPAN_PEER_SERVICE_TAG_NAME)).build()));
		}
	}

	// Instead of going through the list of logs multiple times we're doing it only once
	private void processLogs(Span span, zipkin.Span.Builder zipkinSpan, Endpoint endpoint) {
		boolean notClientOrServer = true;
		boolean hasClientSend = false;
		boolean instanceIdToTag = false;
		for (Log log : span.logs()) {
			if (RPC_EVENTS.contains(log.getEvent())) {
				instanceIdToTag = true;
			}
			if (ZIPKIN_START_EVENTS.contains(log.getEvent())) {
				notClientOrServer = false;
			}
			if (Constants.CLIENT_SEND.equals(log.getEvent())) {
				hasClientSend = !span.tags().containsKey(Constants.SERVER_ADDR);
			}
		}
		if (notClientOrServer) {
			// A zipkin span without any annotations cannot be queried, add special "lc" to avoid that.
			ensureLocalComponent(span, zipkinSpan, endpoint);
		}
		if (hasClientSend) {
			ensureServerAddr(span, zipkinSpan);
		}
		if (instanceIdToTag && this.environment != null) {
			setInstanceIdIfPresent(zipkinSpan, endpoint, Span.INSTANCEID);
		}
	}

	private void setInstanceIdIfPresent(zipkin.Span.Builder zipkinSpan,
			Endpoint endpoint, String key) {
		String property = IdUtils.getDefaultInstanceId(this.environment);
		if (StringUtils.hasText(property)) {
			addZipkinBinaryAnnotation(key, property, endpoint, zipkinSpan);
		}
	}

	/**
	 * Add annotations from the sleuth Span.
	 */
	private void addZipkinAnnotations(zipkin.Span.Builder zipkinSpan,
			Span span, Endpoint endpoint) {
		for (Log ta : span.logs()) {
			Annotation zipkinAnnotation = Annotation.builder()
					.endpoint(endpoint)
					.timestamp(ta.getTimestamp() * 1000) // Zipkin is in microseconds
					.value(ta.getEvent()).build();
			zipkinSpan.addAnnotation(zipkinAnnotation);
		}
	}

	/**
	 * Adds binary annotation from the sleuth Span
	 */
	private void addZipkinBinaryAnnotations(zipkin.Span.Builder zipkinSpan,
			Span span, Endpoint ep) {
		for (Map.Entry<String, String> e : span.tags().entrySet()) {
			addZipkinBinaryAnnotation(e.getKey(), e.getValue(), ep, zipkinSpan);
		}
	}

	private void addZipkinBinaryAnnotation(String key, String value, Endpoint ep,
			zipkin.Span.Builder zipkinSpan) {
		BinaryAnnotation binaryAnn = BinaryAnnotation.builder()
				.type(BinaryAnnotation.Type.STRING)
				.key(key)
				.value(value.getBytes(UTF_8))
				.endpoint(ep).build();
		zipkinSpan.addBinaryAnnotation(binaryAnn);
	}

	/**
	 * There could be instrumentation delay between span creation and the
	 * semantic start of the span (client send). When there's a difference,
	 * spans look confusing. Ex users expect duration to be client
	 * receive - send, but it is a little more than that. Rather than have
	 * to teach each user about the possibility of instrumentation overhead,
	 * we truncate absolute duration (span finish - create) to semantic
	 * duration (client receive - send)
	 */
	private long calculateDurationInMicros(Span span) {
		Log clientSend = hasLog(Span.CLIENT_SEND, span);
		Log clientReceived = hasLog(Span.CLIENT_RECV, span);
		if (clientSend != null && clientReceived != null) {
			return (clientReceived.getTimestamp() - clientSend.getTimestamp()) * 1000;
		}
		return span.getAccumulatedMicros();
	}

	private Log hasLog(String logName, Span span) {
		for (Log log : span.logs()) {
			if (logName.equals(log.getEvent())) {
				return log;
			}
		}
		return null;
	}

	@Override
	public void report(Span span) {
		if (span.isExportable()) {
			this.reporter.report(convert(span));
		} else {
			if (log.isDebugEnabled()) {
				log.debug("The span " + span + " will not be sent to Zipkin due to sampling");
			}
		}
	}
}
