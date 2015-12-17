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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TimelineAnnotation;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.cloud.sleuth.event.ServerReceivedEvent;
import org.springframework.cloud.sleuth.event.ServerSentEvent;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.zipkinCoreConstants;

import lombok.extern.apachecommons.CommonsLog;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class ZipkinSpanListener {

	private SpanCollector spanCollector;
	/**
	 * Endpoint is the visible IP address of this service, the port it is listening on and
	 * the service name from discovery.
	 */
	private Endpoint localEndpoint;

	public ZipkinSpanListener(SpanCollector spanCollector, Endpoint localEndpoint) {
		this.spanCollector = spanCollector;
		this.localEndpoint = localEndpoint;
	}

	@EventListener
	@Order(0)
	public void start(SpanAcquiredEvent event) {
		// Starting a span in zipkin means adding: traceId, id, parentId(optional), and
		// timestamp
		event.getSpan().addTimelineAnnotation("acquire");
	}

	@EventListener
	@Order(0)
	public void serverReceived(ServerReceivedEvent event) {
		if (event.getParent() != null && event.getParent().isRemote()) {
			// If an inbound RPC call, it should log a "sr" annotation.
			// If possible, it should log a binary annotation of "ca", indicating the
			// caller's address (ex X-Forwarded-For header)
			event.getParent().addTimelineAnnotation(zipkinCoreConstants.SERVER_RECV);
		}
	}

	@EventListener
	@Order(0)
	public void clientSend(ClientSentEvent event) {
		// For an outbound RPC call, it should log a "cs" annotation.
		// If possible, it should log a binary annotation of "sa", indicating the
		// destination address.
		event.getSpan().addTimelineAnnotation(zipkinCoreConstants.CLIENT_SEND);
	}

	@EventListener
	@Order(0)
	public void clientReceive(ClientReceivedEvent event) {
		event.getSpan().addTimelineAnnotation(zipkinCoreConstants.CLIENT_RECV);
	}

	@EventListener
	@Order(0)
	public void serverSend(ServerSentEvent event) {
		if (event.getParent() != null && event.getParent().isRemote()) {
			event.getParent().addTimelineAnnotation(zipkinCoreConstants.SERVER_SEND);
			this.spanCollector.collect(convert(event.getParent()));
		}
	}

	@EventListener
	@Order(0)
	public void release(SpanReleasedEvent event) {
		// Ending a span in zipkin means adding duration and sending it out
		event.getSpan().addTimelineAnnotation("release");
		if (event.getSpan().isExportable()) {
			this.spanCollector.collect(convert(event.getSpan()));
		}
	}

	/**
	 * Converts a given Sleuth span to a Zipkin Span.
	 * <ul>
	 * <li>Set ids, etc
	 * <li>Create timeline annotations based on data from Span object.
	 * <li>Create binary annotations based on data from Span object.
	 * </ul>
	 */
	public com.twitter.zipkin.gen.Span convert(Span span) {
		com.twitter.zipkin.gen.Span zipkinSpan = new com.twitter.zipkin.gen.Span();
		addZipkinAnnotations(zipkinSpan, span, this.localEndpoint);
		List<BinaryAnnotation> binaryAnnotationList = createZipkinBinaryAnnotations(span,
				this.localEndpoint);
		zipkinSpan.setDuration(span.getEnd() - span.getBegin());
		zipkinSpan.setTrace_id(hash(span.getTraceId()));
		if (span.getParents().size() > 0) {
			if (span.getParents().size() > 1) {
				log.error("Zipkin doesn't support spans with multiple parents. Omitting "
						+ "other parents for " + span);
			}
			zipkinSpan.setParent_id(hash(span.getParents().get(0)));
		}
		zipkinSpan.setId(hash(span.getSpanId()));
		if (StringUtils.hasText(span.getName())) {
			zipkinSpan.setName(span.getName());
		}
		zipkinSpan.setBinary_annotations(binaryAnnotationList);
		return zipkinSpan;
	}

	/**
	 * Add annotations from the sleuth Span.
	 */
	private void addZipkinAnnotations(com.twitter.zipkin.gen.Span zipkinSpan, Span span,
			Endpoint endpoint) {
		Long startTs = null;
		Long endTs = null;
		for (TimelineAnnotation ta : span.getTimelineAnnotations()) {
			Annotation zipkinAnnotation = createZipkinAnnotation(ta.getMsg(),
					ta.getTime(), endpoint);
			if (zipkinAnnotation.getValue().equals("acquire")) {
				startTs = zipkinAnnotation.getTimestamp();
			}
			else if (zipkinAnnotation.getValue().equals("release")) {
				endTs = zipkinAnnotation.getTimestamp();
			}
			else {
				zipkinSpan.addToAnnotations(zipkinAnnotation);
			}
		}
		if (startTs != null) {
			zipkinSpan.setTimestamp(startTs);
			if (endTs != null) {
				zipkinSpan.setDuration(endTs - zipkinSpan.getTimestamp());
			}
		}
	}

	/**
	 * Creates a list of Annotations that are present in sleuth Span object.
	 *
	 * @return list of Annotations that could be added to Zipkin Span.
	 */
	private List<BinaryAnnotation> createZipkinBinaryAnnotations(Span span,
			Endpoint endpoint) {
		List<BinaryAnnotation> l = new ArrayList<>();
		for (Map.Entry<String, String> e : span.getAnnotations().entrySet()) {
			BinaryAnnotation binaryAnn = new BinaryAnnotation();
			binaryAnn.setAnnotation_type(AnnotationType.STRING);
			binaryAnn.setKey(e.getKey());
			try {
				binaryAnn.setValue(e.getValue().getBytes("UTF-8"));
			}
			catch (UnsupportedEncodingException ex) {
				log.error("Error encoding string as UTF-8", ex);
			}
			binaryAnn.setHost(endpoint);
			l.add(binaryAnn);
		}
		return l;
	}

	/**
	 * Create an annotation with the correct times and endpoint.
	 *
	 * @param value Annotation value
	 * @param time timestamp will be extracted
	 * @param endpoint the endpoint this annotation will be associated with.
	 */
	private static Annotation createZipkinAnnotation(String value, long time,
			Endpoint endpoint) {
		Annotation annotation = new Annotation();
		annotation.setHost(endpoint);

		// Zipkin is in microseconds
		annotation.setTimestamp(time * 1000);
		annotation.setValue(value);
		return annotation;
	}

	private static long hash(String string) {
		long h = 1125899906842597L;
		if (string == null) {
			return h;
		}
		int len = string.length();

		for (int i = 0; i < len; i++) {
			h = 31 * h + string.charAt(i);
		}
		return h;
	}

}
