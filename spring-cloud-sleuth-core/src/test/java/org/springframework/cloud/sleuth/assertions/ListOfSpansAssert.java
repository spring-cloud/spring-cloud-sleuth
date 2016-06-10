/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.assertions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.springframework.cloud.sleuth.Span;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class ListOfSpansAssert extends AbstractAssert<ListOfSpansAssert, ListOfSpans> {

	private static final Log log = LogFactory.getLog(ListOfSpansAssert.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	public ListOfSpansAssert(ListOfSpans actual) {
		super(actual, ListOfSpansAssert.class);
	}

	public static ListOfSpansAssert then(ListOfSpans actual) {
		return new ListOfSpansAssert(actual);
	}

	public ListOfSpansAssert everyParentIdHasItsCorrespondingSpan() {
		isNotNull();
		printSpans();
		List<Long> parentSpanIds = this.actual.spans.stream().flatMap(span -> span.getParents().stream())
				.distinct().collect(toList());
		List<Long> spanIds = this.actual.spans.stream()
				.map(Span::getSpanId).distinct()
				.collect(toList());
		List<Long> difference = new ArrayList<>(parentSpanIds);
		difference.removeAll(spanIds);
		log.info("Difference between parent ids and span ids " +
				difference.stream().map(span -> "id as long [" + span + "] and as hex [" + Span.idToHex(span) + "]").collect(
						joining("\n")));
		Assertions.assertThat(spanIds).containsAll(parentSpanIds);
		return this;
	}

	public ListOfSpansAssert clientSideSpanWithNameHasTags(String name, Map<String, String> tags) {
		isNotNull();
		printSpans();
		List<Span> matchingSpans = this.actual.spans.stream()
				.filter(span -> span.getName().equals(name) && span.logs().stream().filter(entry ->
						entry.getEvent().equals(Span.CLIENT_SEND)).findAny().isPresent()).collect(toList());
		Assertions.assertThat(matchingSpans).isNotEmpty();
		List<Map<String, String>> matchingSpansTags = matchingSpans.stream().map(Span::tags).collect(
				toList());
		Map<String, String> spanTags = new HashMap<>();
		matchingSpansTags.forEach(spanTags::putAll);
		Assertions.assertThat(spanTags.entrySet()).containsAll(tags.entrySet());
		return this;
	}

	private void printSpans() {
		try {
			log.info("Stored spans " + this.objectMapper.writeValueAsString(this.actual.spans));
		}
		catch (JsonProcessingException e) {
		}
	}

}