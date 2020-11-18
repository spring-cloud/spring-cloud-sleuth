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

package org.springframework.cloud.sleuth.brave;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import brave.test.IntegrationTestSpanHandler;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.brave.bridge.BraveAccessor;
import org.springframework.cloud.sleuth.test.TestSpanHandler;

public class BraveTestSpanHandler implements TestSpanHandler {

	final brave.test.TestSpanHandler spans;

	final IntegrationTestSpanHandler integrationSpans;

	public BraveTestSpanHandler(brave.test.TestSpanHandler spans) {
		this.spans = spans;
		this.integrationSpans = null;
	}

	public BraveTestSpanHandler(IntegrationTestSpanHandler integrationSpans) {
		this.spans = null;
		this.integrationSpans = integrationSpans;
	}

	public BraveTestSpanHandler(brave.test.TestSpanHandler spans, IntegrationTestSpanHandler integrationSpans) {
		this.spans = spans;
		this.integrationSpans = integrationSpans;
	}

	@Override
	public List<FinishedSpan> reportedSpans() {
		return this.spans.spans().stream().map(BraveAccessor::finishedSpan).collect(Collectors.toList());
	}

	@Override
	public FinishedSpan takeLocalSpan() {
		return BraveAccessor.finishedSpan(this.integrationSpans.takeLocalSpan());
	}

	@Override
	public void clear() {
		if (this.spans != null) {
			this.spans.clear();
		}
	}

	@Override
	public FinishedSpan takeRemoteSpan(Span.Kind kind) {
		return BraveAccessor.finishedSpan(this.integrationSpans.takeRemoteSpan(brave.Span.Kind.valueOf(kind.name())));
	}

	@Override
	public FinishedSpan takeRemoteSpanWithError(Span.Kind kind) {
		return BraveAccessor
				.finishedSpan(this.integrationSpans.takeRemoteSpanWithError(brave.Span.Kind.valueOf(kind.name())));
	}

	@Override
	public FinishedSpan get(int index) {
		return BraveAccessor.finishedSpan(this.spans.get(index));
	}

	@Override
	public Iterator<FinishedSpan> iterator() {
		return reportedSpans().iterator();
	}

	@Override
	public String toString() {
		return "BraveTestSpanHandler{" + "spans=" + spans + ", integrationSpans=" + integrationSpans + '}';
	}

}
