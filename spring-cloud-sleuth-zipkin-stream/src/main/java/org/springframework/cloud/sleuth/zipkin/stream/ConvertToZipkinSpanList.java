/*
 * Copyright 2016 the original author or authors.
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
		Builder zipkinSpan = zipkin.Span.builder();

		Endpoint ep = Endpoint.create(host.getServiceName(), host.getIpv4(),
				host.getPort().shortValue());

		// A zipkin span without any annotations cannot be queried, add special "lc" to
		// avoid that.
		if (notClientOrServer(span)) {
			ensureLocalComponent(span, zipkinSpan, ep);
		}
		ZipkinMessageListener.addZipkinAnnotations(zipkinSpan, span, ep);
		ZipkinMessageListener.addZipkinBinaryAnnotations(zipkinSpan, span, ep);
		if (hasClientSend(span)) {
			ensureServerAddr(span, zipkinSpan, ep);
		}
		zipkinSpan.timestamp(span.getBegin() * 1000);
		if (!span.isRunning()) { // duration is authoritative, only write when the span stopped
			zipkinSpan.duration(span.getAccumulatedMicros());
		}
		zipkinSpan.traceId(span.getTraceId());
		if (span.getParents().size() > 0) {
			if (span.getParents().size() > 1) {
				log.debug("zipkin doesn't support spans with multiple parents.  Omitting "
						+ "other parents for " + span);
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

	private static void ensureServerAddr(Span span, zipkin.Span.Builder zipkinSpan,
			Endpoint ep) {
		String serviceName = span.tags().containsKey(Span.SPAN_PEER_SERVICE_TAG_NAME)
				? span.tags().get(Span.SPAN_PEER_SERVICE_TAG_NAME) : ep.serviceName;
		Endpoint endpoint = ep.port == null ? Endpoint.create(serviceName, ep.ipv4)
				: Endpoint.create(serviceName, ep.ipv4, ep.port);
		zipkinSpan.addBinaryAnnotation(
				BinaryAnnotation.address(Constants.SERVER_ADDR, endpoint));
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

}