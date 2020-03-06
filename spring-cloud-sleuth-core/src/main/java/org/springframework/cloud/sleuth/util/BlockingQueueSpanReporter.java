/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.util;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import zipkin2.Span;
import zipkin2.reporter.Reporter;

/**
 * Like {@link ArrayListSpanReporter}, except appropriate for async instrumentation.
 */
public class BlockingQueueSpanReporter implements Reporter<Span> {

	private final LinkedBlockingQueue<Span> spans = new LinkedBlockingQueue<>();

	/**
	 * Blocks until a span is reported or throws an {@link AssertionError}.
	 * @return the first span not yet taken.
	 */
	public Span takeSpan() {
		Span result = takeSpan(3_000);
		if (result == null) {
			throw new AssertionError("Span was not reported");
		}
		return result;
	}

	@Override
	public String toString() {
		return "BlockingQueueSpanReporter{spans=" + spans + '}';
	}

	@Override
	public void report(Span span) {
		spans.add(span);
	}

	/** Use this as a post-condition to ensure all spans are accounted for. */
	public void assertEmpty() {
		if (takeSpan(100) != null) {
			throw new AssertionError(
					"Span remaining in queue. Check for redundant reporting!");
		}
	}

	private Span takeSpan(long timeout) {
		Span result;
		try {
			result = spans.poll(timeout, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
		return result;
	}

}
