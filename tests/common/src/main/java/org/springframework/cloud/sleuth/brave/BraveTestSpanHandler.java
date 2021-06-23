/*
 * Copyright 2013-2021 the original author or authors.
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
import java.util.Queue;
import java.util.stream.Collectors;

import brave.test.IntegrationTestSpanHandler;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.brave.bridge.BraveAccessor;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;

import static org.assertj.core.api.BDDAssertions.then;

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
	public void assertAllSpansWereFinishedOrAbandoned(Queue<Span> createdSpans) {
		List<FinishedSpan> finishedSpans = reportedSpans();
		then(finishedSpans).as("There should be that many finished spans as many created ones")
				.hasSize(createdSpans.size());
		// finished -> a,b,c ; created -> b,c,d => matchedFinished = b,c
		List<FinishedSpan> matchedFinishedSpans = finishedSpans.stream()
				.filter(f -> createdSpans.stream().anyMatch(cs -> f.getSpanId().equals(cs.context().spanId())))
				.collect(Collectors.toList());
		// finished -> a,b,c ; created -> b,c,d => matchedCreated = b,c
		List<Span> matchedCreatedSpans = createdSpans.stream()
				.filter(cs -> finishedSpans.stream().anyMatch(f -> cs.context().spanId().equals(f.getSpanId())))
				.collect(Collectors.toList());
		// finished -> a,b,c ; created -> b,c,d => missingFinished = a
		List<FinishedSpan> missingFinishedSpans = finishedSpans.stream()
				.filter(f -> matchedFinishedSpans.stream().noneMatch(m -> m.getSpanId().equals(f.getSpanId())))
				.collect(Collectors.toList());
		// finished -> a,b,c ; created -> b,c,d => missingCreated = d
		List<Span> missingCreatedSpans = createdSpans.stream().filter(
				f -> matchedCreatedSpans.stream().noneMatch(m -> m.context().spanId().equals(f.context().spanId())))
				.collect(Collectors.toList());
		if (!missingFinishedSpans.isEmpty() || !missingCreatedSpans.isEmpty()) {
			throw new AssertionError("There were unmatched created spans " + missingCreatedSpans
					+ " and/or finished span " + missingFinishedSpans);
		}
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
