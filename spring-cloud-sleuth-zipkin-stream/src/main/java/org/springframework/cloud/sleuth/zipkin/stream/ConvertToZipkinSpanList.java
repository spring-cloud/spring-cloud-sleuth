/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.cloud.sleuth.zipkin.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.Host;
import org.springframework.cloud.sleuth.stream.SleuthSink;
import org.springframework.cloud.sleuth.stream.Spans;
import org.springframework.util.StringUtils;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span.Builder;

/**
 * This converts sleuth spans to zipkin ones, skipping invalid or unsampled.
 *
 * @author Adrian Cole
 *
 * @since 1.0.0
 */
final class ConvertToZipkinSpanList {
	private static final List<String> ZIPKIN_START_EVENTS = Arrays
			.asList(Constants.CLIENT_RECV, Constants.SERVER_RECV);

	private static final Log log = org.apache.commons.logging.LogFactory
			.getLog(ConvertToZipkinSpanList.class);

	static List<zipkin.Span> convert(Spans input) {
		Host host = input.getHost();
		List<zipkin.Span> result = new ArrayList<>(input.getSpans().size());
		for (Span span : input.getSpans()) {
			if (!span.getName().equals("message:" + SleuthSink.INPUT)) {
				result.add(convert(span, host));
			}
			else {
				log.warn("Message tracing cycle detected for: " + input);
			}
		}
		return result;
	}

	/**
	 * Converts a given Sleuth span to a Zipkin Span.
	 * <ul>
	 * <li>Set ids, etc
	 * <li>Create timeline annotations based on data from Span object.
	 * <li>Create binary annotations based on data from Span object.
	 * </ul>
	 *
	 * When logging {@link Constants#CLIENT_SEND}, instrumentation should also log the
	 * {@link Constants#SERVER_ADDR} Check <a href=
	 * "https://github.com/openzipkin/zipkin-java/blob/master/zipkin/src/main/java/zipkin/Constants.java#L28">
	 * Zipkin code</a> for more information
	 */
	// VisibleForTesting
	static zipkin.Span convert(Span span, Host host) {
		//TODO: Consider adding support for the debug flag (related to #496)
		Builder zipkinSpan = zipkin.Span.builder();

		Endpoint ep = Endpoint.builder()
				.serviceName(host.getServiceName())
				.ipv4(host.getIpv4())
				.port(host.getPort() != null ? host.getPort() : 0).build();

		// A zipkin span without any annotations cannot be queried, add special "lc" to
		// avoid that.
		if (notClientOrServer(span)) {
			ensureLocalComponent(span, zipkinSpan, ep);
		}
		ZipkinMessageListener.addZipkinAnnotations(zipkinSpan, span, ep);
		ZipkinMessageListener.addZipkinBinaryAnnotations(zipkinSpan, span, ep);
		if (hasClientSend(span)) {
			ensureServerAddr(span, zipkinSpan);
		}
		// In the RPC span model, the client owns the timestamp and duration of the span. If we
		// were propagated an id, we can assume that we shouldn't report timestamp or duration,
		// rather let the client do that. Worst case we were propagated an unreported ID and
		// Zipkin backfills timestamp and duration.
		if (!span.isRemote()) {
			if (Boolean.TRUE.equals(span.isShared())) {
				// don't report server-side timestamp on shared spans
				zipkinSpan.timestamp(null).duration(null);
			} else {
				zipkinSpan.timestamp(span.getBegin() * 1000);
				if (!span.isRunning()) { // duration is authoritative, only write when the span stopped
					zipkinSpan.duration(calculateDurationInMicros(span));
				}
			}
		}
		zipkinSpan.traceIdHigh(span.getTraceIdHigh());
		zipkinSpan.traceId(span.getTraceId());
		if (span.getParents().size() > 0) {
			if (span.getParents().size() > 1) {
				if (log.isDebugEnabled()) {
					log.debug(
							"zipkin doesn't support spans with multiple parents.  Omitting "
									+ "other parents for " + span);
				}
			}
			zipkinSpan.parentId(span.getParents().get(0));
		}
		zipkinSpan.id(span.getSpanId());
		if (StringUtils.hasText(span.getName())) {
			zipkinSpan.name(span.getName());
		}
		return zipkinSpan.build();
	}

	private static void ensureLocalComponent(Span span, Builder zipkinSpan, Endpoint ep) {
		if (span.tags().containsKey(Constants.LOCAL_COMPONENT)) {
			return;
		}
		String processId = span.getProcessId() != null ? span.getProcessId().toLowerCase()
				: ZipkinMessageListener.UNKNOWN_PROCESS_ID;
		zipkinSpan.addBinaryAnnotation(
				BinaryAnnotation.create(Constants.LOCAL_COMPONENT, processId, ep));
	}

	private static void ensureServerAddr(Span span, Builder zipkinSpan) {
		if (span.tags().containsKey(Span.SPAN_PEER_SERVICE_TAG_NAME)) {
			Endpoint endpoint = Endpoint.builder().serviceName(span.tags().get(
					Span.SPAN_PEER_SERVICE_TAG_NAME)).build();
			zipkinSpan.addBinaryAnnotation(
					BinaryAnnotation.address(Constants.SERVER_ADDR, endpoint));
		}
	}

	private static boolean notClientOrServer(Span span) {
		for (org.springframework.cloud.sleuth.Log log : span.logs()) {
			if (ZIPKIN_START_EVENTS.contains(log.getEvent())) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasClientSend(Span span) {
		for (org.springframework.cloud.sleuth.Log log : span.logs()) {
			if (Constants.CLIENT_SEND.equals(log.getEvent())) {
				return !span.tags().containsKey(Constants.SERVER_ADDR);
			}
		}
		return false;
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
	private static long calculateDurationInMicros(Span span) {
		org.springframework.cloud.sleuth.Log clientSend = hasLog(Span.CLIENT_SEND, span);
		org.springframework.cloud.sleuth.Log clientReceived = hasLog(Span.CLIENT_RECV, span);
		if (clientSend != null && clientReceived != null) {
			return (clientReceived.getTimestamp() - clientSend.getTimestamp()) * 1000;
		}
		return span.getAccumulatedMicros();
	}

	private static org.springframework.cloud.sleuth.Log hasLog(String logName, Span span) {
		for (org.springframework.cloud.sleuth.Log log : span.logs()) {
			if (logName.equals(log.getEvent())) {
				return log;
			}
		}
		return null;
	}
}
