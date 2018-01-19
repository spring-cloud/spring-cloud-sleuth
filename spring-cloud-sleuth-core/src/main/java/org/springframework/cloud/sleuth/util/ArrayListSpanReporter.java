/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.util;

import java.util.ArrayList;
import java.util.List;

import zipkin2.Span;
import zipkin2.reporter.Reporter;

/**
 * Accumulator of closed spans.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public class ArrayListSpanReporter implements Reporter<Span> {
	private final List<Span> spans = new ArrayList<>();

	public List<Span> getSpans() {
		synchronized (this.spans) {
			return new ArrayList<>(this.spans);
		}
	}

	@Override
	public String toString() {
		return "ArrayListSpanAccumulator{" +
				"spans=" + getSpans() +
				'}';
	}

	@Override
	public void report(Span span) {
		synchronized (this.spans) {
			this.spans.add(span);
		}
	}

	public void clear() {
		synchronized (this.spans) {
			this.spans.clear();
		}
	}
}
