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

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.Host;
import org.springframework.cloud.sleuth.stream.SleuthSink;
import org.springframework.cloud.sleuth.stream.Spans;
import org.springframework.util.StringUtils;

import lombok.extern.apachecommons.CommonsLog;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Sampler;
import zipkin.Span.Builder;

/**
 * This converts sleuth spans to zipkin ones, skipping invalid or unsampled.
 */
@CommonsLog
final class SamplingZipkinSpanIterator implements Iterator<zipkin.Span> {

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
		if (!input.getName().equals("message/" + SleuthSink.INPUT)) {
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
			String processId = span.getProcessId() != null
					? span.getProcessId().toLowerCase()
					: ZipkinMessageListener.UNKNOWN_PROCESS_ID;
			zipkinSpan.addBinaryAnnotation(
					BinaryAnnotation.create(Constants.LOCAL_COMPONENT, processId, ep));
		}
		else {
			ZipkinMessageListener.addZipkinAnnotations(zipkinSpan, span, ep);
			ZipkinMessageListener.addZipkinBinaryAnnotations(zipkinSpan, span, ep);
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
}