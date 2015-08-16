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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.apachecommons.CommonsLog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TimelineAnnotation;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
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

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class ZipkinSpanListener {

	private SpanCollector spanCollector;
	@Value("${spring.application.name:application}")
	private String appName;
	@Autowired
	private ServerProperties serverProperties;

	public ZipkinSpanListener(SpanCollector spanCollector) {
		this.spanCollector = spanCollector;
	}

	@EventListener
	@Order(0)
	public void start(SpanAcquiredEvent event) {
		if (event.getParent() != null && event.getParent().isRemote()) {
			event.getParent().addTimelineAnnotation(zipkinCoreConstants.SERVER_RECV);
		}
		event.getSpan().addTimelineAnnotation("acquire");
	}

	@EventListener
	@Order(0)
	public void clientSend(ClientSentEvent event) {
		event.getSpan().addTimelineAnnotation(zipkinCoreConstants.CLIENT_SEND);
	}

	@EventListener
	@Order(0)
	public void clientReceive(ClientReceivedEvent event) {
		event.getSpan().addTimelineAnnotation(zipkinCoreConstants.CLIENT_RECV);
	}

	@EventListener
	@Order(0)
	public void release(SpanReleasedEvent event) {
		if (event.getParent() != null && event.getParent().isRemote()) {
			event.getParent().addTimelineAnnotation(zipkinCoreConstants.SERVER_SEND);
			this.spanCollector.collect(convert(event.getParent()));
		}
		event.getSpan().addTimelineAnnotation("release");
		this.spanCollector.collect(convert(event.getSpan()));
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

		String serviceName = getServiceName(span);
		int address = getAddress();
		Integer port = getPort();

		Endpoint ep = new Endpoint(address, port.shortValue(), serviceName);
		List<Annotation> annotationList = createZipkinAnnotations(span, ep);
		List<BinaryAnnotation> binaryAnnotationList = createZipkinBinaryAnnotations(span,
				ep);
		zipkinSpan.setTrace_id(hash(span.getTraceId()));
		if (span.getParents().size() > 0) {
			if (span.getParents().size() > 1) {
				log.error("zipkin doesn't support spans with multiple parents.  Omitting "
						+ "other parents for " + span);
			}
			zipkinSpan.setParent_id(hash(span.getParents().get(0)));
		}
		zipkinSpan.setId(hash(span.getSpanId()));
		if (StringUtils.hasText(span.getName())) {
			zipkinSpan.setName(span.getName());
		}
		zipkinSpan.setAnnotations(annotationList);
		zipkinSpan.setBinary_annotations(binaryAnnotationList);
		return zipkinSpan;
	}

	public Integer getPort() {
		Integer port;
		if (this.serverProperties.getPort() != null) {
			port = this.serverProperties.getPort();
		}
		else {
			port = 8080; // TODO: support random port
		}
		return port;
	}

	public int getAddress() {
		String address;
		if (this.serverProperties.getAddress() != null) {
			address = this.serverProperties.getAddress().getHostAddress();
		}
		else {
			address = "127.0.0.1"; // TODO: get address from config
		}
		return ipAddressToInt(address);
	}

	public String getServiceName(Span span) {
		String serviceName;
		if (span.getProcessId() != null) {
			serviceName = span.getProcessId().toLowerCase();
		}
		else {
			serviceName = this.appName;
		}
		return serviceName;
	}

	private int ipAddressToInt(final String ip) {
		InetAddress inetAddress = null;
		try {
			inetAddress = InetAddress.getByName(ip);
		}
		catch (final UnknownHostException e) {
			throw new IllegalArgumentException(e);
		}
		return ByteBuffer.wrap(inetAddress.getAddress()).getInt();
	}

	/**
	 * Add annotations from the sleuth Span.
	 */
	private List<Annotation> createZipkinAnnotations(Span span, Endpoint endpoint) {
		List<Annotation> annotationList = new ArrayList<>();

		long srTime = 0, csTime = 0;
		// add sleuth time annotation
		for (TimelineAnnotation ta : span.getTimelineAnnotations()) {
			Annotation zipkinAnnotation = createZipkinAnnotation(ta.getMsg(),
					ta.getTime(), 0, endpoint, true);
			if (zipkinCoreConstants.SERVER_RECV.equals(ta.getMsg())) {
				srTime = ta.getTime();
			}
			if (zipkinCoreConstants.SERVER_SEND.equals(ta.getMsg()) && srTime != 0) {
				zipkinAnnotation
				.setDuration(new Long(ta.getTime() - srTime).intValue() * 1000);
			}
			if (zipkinCoreConstants.CLIENT_SEND.equals(ta.getMsg())) {
				csTime = ta.getTime();
			}
			if (zipkinCoreConstants.CLIENT_RECV.equals(ta.getMsg()) && csTime != 0) {
				zipkinAnnotation
				.setDuration(new Long(ta.getTime() - csTime).intValue() * 1000);
			}
			annotationList.add(zipkinAnnotation);
		}
		return annotationList;
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
			binaryAnn.setAnnotation_type(AnnotationType.BYTES);
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
	 * @param sendRequest use the first or last timestamp.
	 */
	private static Annotation createZipkinAnnotation(String value, long time,
			int duration, Endpoint endpoint, boolean sendRequest) {
		Annotation annotation = new Annotation();
		annotation.setHost(endpoint);

		// Zipkin is in microseconds
		if (sendRequest) {
			annotation.setTimestamp(time * 1000);
		}
		else {
			annotation.setTimestamp(time * 1000);
		}

		if (duration > 0) {
			annotation.setDuration(duration * 1000);
		}
		annotation.setValue(value);
		return annotation;
	}

	private static long hash(String string) {
		long h = 1125899906842597L;
		int len = string.length();

		for (int i = 0; i < len; i++) {
			h = 31 * h + string.charAt(i);
		}
		return h;
	}

}
