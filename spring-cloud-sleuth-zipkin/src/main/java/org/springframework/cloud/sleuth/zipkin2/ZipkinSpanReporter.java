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

package org.springframework.cloud.sleuth.zipkin2;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.commons.util.IdUtils;
import org.springframework.cloud.sleuth.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAdjuster;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import zipkin2.Endpoint;
import zipkin2.reporter.Reporter;

/**
 * Listener of Sleuth events. Reports to Zipkin via {@link Reporter}.
 */
public class ZipkinSpanReporter implements SpanReporter {
	private static final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory
			.getLog(ZipkinSpanReporter.class);

	private final Reporter<zipkin2.Span> reporter;
	private final Environment environment;
	private final List<SpanAdjuster> spanAdjusters;
	/**
	 * Endpoint is the visible IP address of this service, the port it is listening on and
	 * the service name from discovery.
	 */
	// Visible for testing
	final EndpointLocator endpointLocator;

	public ZipkinSpanReporter(Reporter<zipkin2.Span> reporter, EndpointLocator endpointLocator,
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
	 * <li>Create tags based on data from Span object.
	 * </ul>
	 */
	// Visible for testing
	zipkin2.Span convert(Span span) {
		//TODO: Consider adding support for the debug flag (related to #496)
		Span convertedSpan = span;
		for (SpanAdjuster adjuster : this.spanAdjusters) {
			convertedSpan = adjuster.adjust(convertedSpan);
		}
		zipkin2.Span.Builder zipkinSpan = zipkin2.Span.newBuilder();
		zipkinSpan.localEndpoint(this.endpointLocator.local());
		processLogs(convertedSpan, zipkinSpan);
		addZipkinTags(zipkinSpan, convertedSpan);
		if (zipkinSpan.kind() != null && this.environment != null) {
			setInstanceIdIfPresent(zipkinSpan, Span.INSTANCEID);
		}
		zipkinSpan.shared(convertedSpan.isShared());
		zipkinSpan.timestamp(convertedSpan.getBegin() * 1000L);
		if (!convertedSpan.isRunning()) { // duration is authoritative, only write when the span stopped
			zipkinSpan.duration(calculateDurationInMicros(convertedSpan));
		}
		zipkinSpan.traceId(convertedSpan.traceIdString());
		if (convertedSpan.getParents().size() > 0) {
			if (convertedSpan.getParents().size() > 1) {
				log.error("Zipkin doesn't support spans with multiple parents. Omitting "
						+ "other parents for " + convertedSpan);
			}
			zipkinSpan.parentId(Span.idToHex(convertedSpan.getParents().get(0)));
		}
		zipkinSpan.id(Span.idToHex(convertedSpan.getSpanId()));
		if (StringUtils.hasText(convertedSpan.getName())) {
			zipkinSpan.name(convertedSpan.getName());
		}
		return zipkinSpan.build();
	}

	// Instead of going through the list of logs multiple times we're doing it only once
	void processLogs(Span span, zipkin2.Span.Builder zipkinSpan) {
		for (Log log : span.logs()) {
			String event = log.getEvent();
			long micros = log.getTimestamp() * 1000L;
			// don't add redundant annotations to the output
			if (event.length() == 2) {
				if (event.equals("cs")) {
					zipkinSpan.kind(zipkin2.Span.Kind.CLIENT);
				} else if (event.equals("sr")) {
					zipkinSpan.kind(zipkin2.Span.Kind.SERVER);
				} else if (event.equals("ss")) {
					zipkinSpan.kind(zipkin2.Span.Kind.SERVER);
				} else if (event.equals("cr")) {
					zipkinSpan.kind(zipkin2.Span.Kind.CLIENT);
				} else if (event.equals("ms")) {
					zipkinSpan.kind(zipkin2.Span.Kind.PRODUCER);
				} else if (event.equals("mr")) {
					zipkinSpan.kind(zipkin2.Span.Kind.CONSUMER);
				} else {
					zipkinSpan.addAnnotation(micros, event);
				}
			} else {
				zipkinSpan.addAnnotation(micros, event);
			}
		}
	}

	private void setInstanceIdIfPresent(zipkin2.Span.Builder zipkinSpan, String key) {
		String property = defaultInstanceId();
		if (StringUtils.hasText(property)) {
			zipkinSpan.putTag(key, property);
		}
	}

	String defaultInstanceId() {
		return IdUtils.getDefaultInstanceId(this.environment);
	}

	/**
	 * Adds tags from the sleuth Span
	 */
	private void addZipkinTags(zipkin2.Span.Builder zipkinSpan, Span span) {
		Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();
		boolean shouldAddRemote = false;
		// don't add redundant tags to the output
		for (Map.Entry<String, String> e : span.tags().entrySet()) {
			String key = e.getKey();
			if (key.equals("peer.service")) {
				shouldAddRemote = true;
				remoteEndpoint.serviceName(e.getValue());
			} else if (key.equals("peer.ipv4") || key.equals("peer.ipv6")) {
				shouldAddRemote = true;
				remoteEndpoint.ip(e.getValue());
			} else if (key.equals("peer.port")) {
				shouldAddRemote = true;
				try {
					remoteEndpoint.port(Integer.parseInt(e.getValue()));
				} catch (NumberFormatException ignored) {
				}
			} else {
				zipkinSpan.putTag(e.getKey(), e.getValue());
			}
		}
		if (shouldAddRemote) {
			zipkinSpan.remoteEndpoint(remoteEndpoint.build());
		}
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

	@Override
	public String toString(){
		return "ZipkinSpanReporter(" + this.reporter + ")";
	}
}
