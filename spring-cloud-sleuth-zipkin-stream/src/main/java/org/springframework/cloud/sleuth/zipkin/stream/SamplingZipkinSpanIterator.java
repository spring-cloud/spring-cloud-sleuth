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

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.Host;
import org.springframework.cloud.sleuth.stream.SleuthSink;
import org.springframework.cloud.sleuth.stream.Spans;
import zipkin.Sampler;

import java.util.Iterator;
import java.util.NoSuchElementException;

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
			zipkin.Span result = ZipkinMessageListener.convert(input, host);
			if (this.sampler.isSampled(result.traceId)) {
				return result;
			}
		}
		else {
			log.warn("Message tracing cycle detected for: " + input);
		}
		return null;
	}
}