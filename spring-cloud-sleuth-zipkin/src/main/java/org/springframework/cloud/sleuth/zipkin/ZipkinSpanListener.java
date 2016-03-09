/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.cloud.sleuth.event.ServerReceivedEvent;
import org.springframework.cloud.sleuth.event.ServerSentEvent;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;

/**
 * Listener of Sleuth events. Reports to Zipkin via {@link ZipkinSpanReporter}.
 *
 * @author Spencer Gibb
 *
 * @since 1.0.0
 */
public class ZipkinSpanListener {
	private static final List<String> ZIPKIN_START_EVENTS = Arrays.asList(
			Constants.CLIENT_RECV, Constants.SERVER_RECV
	);

	private static final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory
			.getLog(ZipkinSpanListener.class);
	private static final Charset UTF_8 = Charset.forName("UTF-8");
	private static final byte[] UNKNOWN_BYTES = "unknown".getBytes(UTF_8);

	private ZipkinSpanReporter reporter;
	/**
	 * Endpoint is the visible IP address of this service, the port it is listening on and
	 * the service name from discovery.
	 */
	// Visible for testing
	EndpointLocator endpointLocator;

	public ZipkinSpanListener(ZipkinSpanReporter reporter, EndpointLocator endpointLocator) {
		this.reporter = reporter;
		this.endpointLocator = endpointLocator;
	}

	@EventListener
	@Order(0)
	public void start(SpanAcquiredEvent event) {
		// Zipkin Span.timestamp corresponds with Sleuth's Span.begin
		assert event.getSpan().getBegin() != 0;
	}

	@EventListener
	@Order(0)
	public void serverReceived(ServerReceivedEvent event) {
		if (event.getParent() != null && event.getParent().isRemote()) {
			// If an inbound RPC call, it should log a "sr" annotation.
			// If possible, it should log a binary annotation of "ca", indicating the
			// caller's address (ex X-Forwarded-For header)
			event.getParent().logEvent(Constants.SERVER_RECV);
		}
	}

	@EventListener
	@Order(0)
	public void clientSend(ClientSentEvent event) {
		// For an outbound RPC call, it should log a "cs" annotation.
		// If possible, it should log a binary annotation of "sa", indicating the
		// destination address.
		event.getSpan().logEvent(Constants.CLIENT_SEND);
	}

	@EventListener
	@Order(0)
	public void clientReceive(ClientReceivedEvent event) {
		event.getSpan().logEvent(Constants.CLIENT_RECV);
	}

	@EventListener
	@Order(0)
	public void serverSend(ServerSentEvent event) {
		if (event.getParent() != null && event.getParent().isRemote()) {
			event.getParent().logEvent(Constants.SERVER_SEND);
			this.reporter.report(convert(event.getParent()));
		}
	}

	@EventListener
	@Order(0)
	public void release(SpanReleasedEvent event) {
		// Ending a span in zipkin means adding duration and sending it out
		// Zipkin Span.duration corresponds with Sleuth's Span.begin and end
		assert event.getSpan().getEnd() != 0;
		if (event.getSpan().isExportable()) {
			this.reporter.report(convert(event.getSpan()));
		}
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
		zipkin.Span.Builder zipkinSpan = new zipkin.Span.Builder();

		// A zipkin span without any annotations cannot be queried, add special "lc" to avoid that.
		if (notClientOrServer(span)) {
			ensureLocalComponent(span, zipkinSpan);
		}
		addZipkinAnnotations(zipkinSpan, span, this.endpointLocator.local());
		addZipkinBinaryAnnotations(zipkinSpan, span, this.endpointLocator.local());
		if (hasClientSend(span)) {
			ensureServerAddr(span, zipkinSpan);
		}
		zipkinSpan.timestamp(span.getBegin() * 1000L);
		zipkinSpan.duration(span.getAccumulatedMillis() * 1000L);
		zipkinSpan.traceId(span.getTraceId());
		if (span.getParents().size() > 0) {
			if (span.getParents().size() > 1) {
				log.error("Zipkin doesn't support spans with multiple parents. Omitting "
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

	private void ensureLocalComponent(Span span, zipkin.Span.Builder zipkinSpan) {
		if (span.tags().containsKey(Constants.LOCAL_COMPONENT)) {
			return;
		}
		byte[] processId = span.getProcessId() != null
				? span.getProcessId().toLowerCase().getBytes(UTF_8)
				: UNKNOWN_BYTES;
		BinaryAnnotation component = new BinaryAnnotation.Builder()
				.type(BinaryAnnotation.Type.STRING)
				.key("lc") // LOCAL_COMPONENT
				.value(processId)
				.endpoint(this.endpointLocator.local()).build();
		zipkinSpan.addBinaryAnnotation(component);
	}

	private void ensureServerAddr(Span span, zipkin.Span.Builder zipkinSpan) {
		String serviceName = span.tags().containsKey(Span.SPAN_PEER_SERVICE_TAG_NAME) ?
				span.tags().get(Span.SPAN_PEER_SERVICE_TAG_NAME) : this.endpointLocator.local().serviceName;
		zipkinSpan.addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR,
				Endpoint.create(serviceName, this.endpointLocator.local().ipv4, this.endpointLocator.local().port)));
	}

	private boolean notClientOrServer(Span span) {
		for (Log log : span.logs()) {
			if (ZIPKIN_START_EVENTS.contains(log.getEvent())) {
				return false;
			}
		}
		return true;
	}

	private boolean hasClientSend(Span span) {
		for (org.springframework.cloud.sleuth.Log log : span.logs()) {
			if (Constants.CLIENT_SEND.equals(log.getEvent())) {
				return !span.tags().containsKey(Constants.SERVER_ADDR);
			}
		}
		return false;
	}

	/**
	 * Add annotations from the sleuth Span.
	 */
	private void addZipkinAnnotations(zipkin.Span.Builder zipkinSpan,
			Span span, Endpoint endpoint) {
		for (Log ta : span.logs()) {
			Annotation zipkinAnnotation = new Annotation.Builder()
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
			BinaryAnnotation binaryAnn = new BinaryAnnotation.Builder()
					.type(BinaryAnnotation.Type.STRING)
					.key(e.getKey())
					.value(e.getValue().getBytes(UTF_8))
					.endpoint(ep).build();
			zipkinSpan.addBinaryAnnotation(binaryAnn);
		}
	}

}
