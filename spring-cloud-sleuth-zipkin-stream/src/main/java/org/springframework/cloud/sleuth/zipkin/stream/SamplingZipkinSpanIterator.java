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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.Host;
import org.springframework.cloud.sleuth.stream.SleuthSink;
import org.springframework.cloud.sleuth.stream.Spans;
import org.springframework.util.StringUtils;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Sampler;
import zipkin.Span.Builder;

/**
 * This converts sleuth spans to zipkin ones, skipping invalid or unsampled.
 *
 * @author Adrian Cole
 *
 * @since 1.0.0
 */
final class SamplingZipkinSpanIterator implements Iterator<zipkin.Span> {
	private static final List<String> ZIPKIN_CLIENT_ANNOTATIONS = Arrays.asList(
			Constants.CLIENT_ADDR, Constants.CLIENT_RECV, Constants.CLIENT_SEND,
			Constants.CLIENT_RECV_FRAGMENT, Constants.CLIENT_SEND_FRAGMENT
	);
	private static final List<String> ZIPKIN_ANNOTATIONS = zipkinAnnotations();

	private static List<String> zipkinAnnotations() {
		List<String> annotations = new ArrayList<>();
		annotations.addAll(Arrays.asList(
				Constants.SERVER_ADDR, Constants.SERVER_RECV, Constants.SERVER_SEND,
				Constants.SERVER_RECV_FRAGMENT, Constants.SERVER_SEND_FRAGMENT,
				Constants.LOCAL_COMPONENT,
				Constants.WIRE_RECV, Constants.WIRE_SEND
		));
		annotations.addAll(ZIPKIN_CLIENT_ANNOTATIONS);
		return annotations;
	}

	private static final Log log = org.apache.commons.logging.LogFactory
			.getLog(SamplingZipkinSpanIterator.class);

	private final Sampler sampler;
	private final Iterator<Span> delegate;
	private final Host host;
	private zipkin.Span peeked;

	SamplingZipkinSpanIterator(Sampler sampler, Spans input) {
		this.sampler = sampler;
		this.delegate = input.getSpans().iterator();
		this.host = input.getHost();
	}

	@Override
	public boolean hasNext() {
		while (this.peeked == null && this.delegate.hasNext()) {
			this.peeked = convertAndSample(this.delegate.next(), this.host);
		}
		return this.peeked != null;
	}

	@Override
	public zipkin.Span next() {
		// implicitly peeks
		if (!hasNext())
			throw new NoSuchElementException();
		zipkin.Span result = this.peeked;
		this.peeked = null;
		return result;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("remove");
	}

	/**
	 * returns a converted span or null if it is invalid or unsampled.
	 */
	zipkin.Span convertAndSample(Span input, Host host) {
		if (!input.getName().equals("message:" + SleuthSink.INPUT)) {
			zipkin.Span result = SamplingZipkinSpanIterator.convert(input, host);
			if (this.sampler.isSampled(result.traceId)) {
				return result;
			}
		}
		else {
			log.warn("Message tracing cycle detected for: " + input);
		}
		return null;
	}

	/**
	 * Converts a given Sleuth span to a Zipkin Span.
	 * <ul>
	 * <li>Set ids, etc
	 * <li>Create timeline annotations based on data from Span object.
	 * <li>Create binary annotations based on data from Span object.
	 * </ul>
	 */
	// VisibleForTesting
	static zipkin.Span convert(Span span, Host host) {
		Builder zipkinSpan = new zipkin.Span.Builder();

		Endpoint ep = Endpoint.create(host.getServiceName(), host.getIpv4(),
				host.getPort().shortValue());

		// A zipkin span without any annotations cannot be queried, add special "lc" to
		// avoid that.
		if (span.logs().isEmpty() && span.tags().isEmpty()) {
			addLocalComponentAnnotation(span, zipkinSpan, ep);
		}
		else {
			ZipkinMessageListener.addZipkinAnnotations(zipkinSpan, span, ep);
			ZipkinMessageListener.addZipkinBinaryAnnotations(zipkinSpan, span, ep);
		}
		if (!spanContainsAnyZipkinConstant(span)) {
			addLocalComponentAnnotation(span, zipkinSpan, ep);
		} else if (spanContainsAnyZipkinClientConstantAndSaIsNotSet(span)) {
			addServerAddressAnnotation(zipkinSpan, ep);
		}
		zipkinSpan.timestamp(span.getBegin() * 1000);
		zipkinSpan.duration(span.getAccumulatedMillis() * 1000);
		zipkinSpan.traceId(span.getTraceId());
		if (span.getParents().size() > 0) {
			if (span.getParents().size() > 1) {
				SamplingZipkinSpanIterator.log
						.debug("zipkin doesn't support spans with multiple parents.  Omitting "
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

	private static void addLocalComponentAnnotation(Span span, Builder zipkinSpan,
			Endpoint ep) {
		String processId = span.getProcessId() != null
				? span.getProcessId().toLowerCase()
				: ZipkinMessageListener.UNKNOWN_PROCESS_ID;
		zipkinSpan.addBinaryAnnotation(
				BinaryAnnotation.create(Constants.LOCAL_COMPONENT, processId, ep));
	}

	private static void addServerAddressAnnotation(zipkin.Span.Builder zipkinSpan,
			Endpoint ep) {
		BinaryAnnotation component = new BinaryAnnotation.Builder()
				.type(BinaryAnnotation.Type.STRING)
				.key(Constants.SERVER_ADDR)
				.value(ep.serviceName)
				.endpoint(ep).build();
		zipkinSpan.addBinaryAnnotation(component);
	}

	private static boolean spanContainsAnyZipkinConstant(Span span) {
		for (org.springframework.cloud.sleuth.Log log : span.logs()) {
			if (ZIPKIN_ANNOTATIONS.contains(log.getEvent())) {
				return true;
			}
		}
		return false;
	}

	private static boolean spanContainsAnyZipkinClientConstantAndSaIsNotSet(Span span) {
		boolean containsAnyZipkinClientConstant = false;
		for (org.springframework.cloud.sleuth.Log log : span.logs()) {
			if (ZIPKIN_CLIENT_ANNOTATIONS.contains(log.getEvent())) {
				containsAnyZipkinClientConstant = true;
				break;
			}
		}
		return containsAnyZipkinClientConstant && !span.tags().containsKey(Constants.SERVER_ADDR);
	}


}