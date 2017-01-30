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
import org.springframework.cloud.sleuth.Span;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

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
		assertThat(spanIds).containsAll(parentSpanIds);
		return this;
	}

	public ListOfSpansAssert clientSideSpanWithNameHasTags(String name, Map<String, String> tags) {
		isNotNull();
		printSpans();
		List<Span> matchingSpans = this.actual.spans.stream()
				.filter(span -> span.getName().equals(name) && span.logs().stream().filter(entry ->
						entry.getEvent().equals(Span.CLIENT_SEND)).findAny().isPresent()).collect(toList());
		assertThat(matchingSpans).isNotEmpty();
		List<Map<String, String>> matchingSpansTags = matchingSpans.stream().map(Span::tags).collect(
				toList());
		Map<String, String> spanTags = new HashMap<>();
		matchingSpansTags.forEach(spanTags::putAll);
		assertThat(spanTags.entrySet()).containsAll(tags.entrySet());
		return this;
	}

	public ListOfSpansAssert hasASpanWithTagKeyEqualTo(String tagKey) {
		isNotNull();
		printSpans();
		if (!spanWithKeyTagExists(tagKey)) {
			failWithMessage("Expected spans \n <%s> \nto contain at least one span with tag key "
					+ "equal to <%s>", spansToString(), tagKey);
		}
		return this;
	}

	private boolean spanWithKeyTagExists(String tagKey) {
		for (Span span : this.actual.spans) {
			if (span.tags().containsKey(tagKey)) {
				return true;
			}
		}
		return false;
	}

	public ListOfSpansAssert hasASpanWithTagEqualTo(String tagKey, String tagValue) {
		isNotNull();
		printSpans();
		List<Span> matchingSpans = this.actual.spans.stream()
				.filter(span -> tagValue.equals(span.tags().get(tagKey)))
				.collect(toList());
		if (matchingSpans.isEmpty()) {
			failWithMessage("Expected spans \n <%s> \nto contain at least one span with tag key "
					+ "equal to <%s> and value equal to <%s>.\n\n", spansToString(), tagKey, tagValue);
		}
		return this;
	}

	private String spansToString() {
		return this.actual.spans.stream().map(span ->  "\nSPAN: " + span.toString() + " with name [" + span.getName() + "] " +
				"\nwith tags " + span.tags() + "\nwith logs " + span.logs()).collect(joining("\n"));
	}

	public ListOfSpansAssert doesNotHaveASpanWithName(String name) {
		isNotNull();
		printSpans();
		List<Span> matchingSpans = findSpansWithName(name);
		if (!matchingSpans.isEmpty()) {
			failWithMessage("Expected spans \n <%s> \nnot to contain a span with name <%s>", spansToString(), name);
		}
		return this;
	}

	private List<Span> findSpansWithName(String name) {
		return this.actual.spans.stream()
				.filter(span -> span.getName().equals(name))
				.collect(toList());
	}

	public ListOfSpansAssert hasASpanWithName(String name) {
		isNotNull();
		printSpans();
		List<Span> matchingSpans = findSpansWithName(name);
		if (matchingSpans.isEmpty()) {
			failWithMessage("Expected spans <%s> to contain a span with name <%s>", spansToString(), name);
		}
		return this;
	}

	public ListOfSpansAssert hasRpcTagsInProperOrder() {
		isNotNull();
		printSpans();
		RpcLogKeeper rpcLogKeeper = findRpcLogs();
		rpcLogKeeper.assertThatFullRpcCycleTookPlace();
		rpcLogKeeper.assertThatRpcLogsTookPlaceInOrder();
		return this;
	}

	private void printSpans() {
		try {
			log.info("Stored spans " + this.objectMapper.writeValueAsString(new ArrayList<>(this.actual.spans)));
		}
		catch (JsonProcessingException e) {
		}
	}

	@Override
	protected void failWithMessage(String errorMessage, Object... arguments) {
		log.error(String.format(errorMessage, arguments));
		super.failWithMessage(errorMessage, arguments);
	}

	RpcLogKeeper findRpcLogs() {
		final RpcLogKeeper rpcLogKeeper = new RpcLogKeeper();
		this.actual.spans.forEach(span -> span.logs().forEach(log -> {
			switch (log.getEvent()) {
			case Span.CLIENT_SEND:
				rpcLogKeeper.cs = log;
				break;
			case Span.SERVER_RECV:
				rpcLogKeeper.sr = log;
				break;
			case Span.SERVER_SEND:
				rpcLogKeeper.ss = log;
				break;
			case Span.CLIENT_RECV:
				rpcLogKeeper.cr = log;
				break;
			default:
				break;
			}
		}));
		return rpcLogKeeper;
	}
}

class RpcLogKeeper {
	org.springframework.cloud.sleuth.Log cs;
	org.springframework.cloud.sleuth.Log sr;
	org.springframework.cloud.sleuth.Log ss;
	org.springframework.cloud.sleuth.Log cr;

	void assertThatFullRpcCycleTookPlace() {
		assertThat(this.cs).describedAs("Client Send log").isNotNull();
		assertThat(this.sr).describedAs("Server Received log").isNotNull();
		assertThat(this.ss).describedAs("Server Send log").isNotNull();
		assertThat(this.cr).describedAs("Client Received log").isNotNull();
	}

	void assertThatRpcLogsTookPlaceInOrder() {
		long csTimestamp = this.cs.getTimestamp();
		long srTimestamp = this.sr.getTimestamp();
		long ssTimestamp = this.ss.getTimestamp();
		long crTimestamp = this.cr.getTimestamp();
		assertThat(csTimestamp).as("CS timestamp should be before SR timestamp").isLessThanOrEqualTo(srTimestamp);
		assertThat(srTimestamp).as("SR timestamp should be before SS timestamp").isLessThanOrEqualTo(ssTimestamp);
		assertThat(ssTimestamp).as("SS timestamp should be before CR timestamp").isLessThanOrEqualTo(crTimestamp);
	}
}