
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

package org.springframework.cloud.sleuth.test;

import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;

import static org.assertj.core.api.BDDAssertions.then;

public interface TestSpanHandler extends Iterable<FinishedSpan> {

	List<FinishedSpan> reportedSpans();

	FinishedSpan takeLocalSpan();

	void clear();

	FinishedSpan takeRemoteSpan(Span.Kind kind);

	FinishedSpan takeRemoteSpanWithError(Span.Kind kind);

	FinishedSpan get(int index);

	default void assertAllSpansWereFinishedOrAbandoned(Queue<Span> createdSpans) {
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

}
