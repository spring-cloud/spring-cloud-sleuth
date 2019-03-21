/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.assertions;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.AbstractAssert;
import org.springframework.cloud.sleuth.Span;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class ListOfSpansAssert extends AbstractAssert<ListOfSpansAssert, ListOfSpans> {

	private static final Log log = LogFactory.getLog(ListOfSpansAssert.class);

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
				.filter(span -> span.getName().equals(name) && span.logs().stream().anyMatch(entry ->
						entry.getEvent().equals(Span.CLIENT_SEND))).collect(toList());
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

	public ListOfSpansAssert everySpanHasABaggage(String baggageKey, String baggageValue) {
		isNotNull();
		printSpans();
		if (!everySpanHasBaggage(baggageKey, baggageValue)) {
			failWithMessage("Expected spans \n <%s> \nto ALL contain baggage with key "
					+ "equal to <%s>, and value equal to <%s>", spansToString(), baggageKey, baggageValue);
		}
		return this;
	}

	public ListOfSpansAssert anySpanHasABaggage(String baggageKey, String baggageValue) {
		isNotNull();
		printSpans();
		if (!hasBaggage(baggageKey, baggageValue)) {
			failWithMessage("Expected spans \n <%s> \nto contain at least one span with baggage key "
					+ "equal to <%s>, and value equal to <%s>", spansToString(), baggageKey, baggageValue);
		}
		return this;
	}

	public ListOfSpansAssert allSpansAreExportable() {
		isNotNull();
		printSpans();
		if (!everySpanIsExportable()) {
			failWithMessage("Expected spans \n <%s> \nto be exportable but there's at least "
					+ "one which is not", spansToString());
		}
		return this;
	}

	public ListOfSpansAssert allSpansHaveTraceId(long traceId) {
		isNotNull();
		printSpans();
		if (!everySpanHasTraceId(traceId)) {
			failWithMessage("Expected spans \n <%s> \nto have trace id <%s> but there's at least "
					+ "one which doesn't have it", spansToString(), traceId);
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

	private boolean everySpanHasBaggage(String baggageKey, String baggageValue) {
		boolean exists = false;
		for (Span span : this.actual.spans) {
			for (Map.Entry<String, String> baggage : span.baggageItems()) {
				if (baggage.getKey().equals(baggageKey)) {
					if (baggage.getValue().equals(baggageValue)) {
						exists = true;
						break;
					}
				}
			}
			if (!exists) {
				return false;
			}
		}
		return exists;
	}

	private boolean everySpanIsExportable() {
		for (Span span : this.actual.spans) {
			if (!span.isExportable()) {
				return false;
			}
		}
		return true;
	}

	private boolean everySpanHasTraceId(long traceId) {
		for (Span span : this.actual.spans) {
			if (span.getTraceId() != traceId) {
				return false;
			}
		}
		return true;
	}

	private boolean hasBaggage(String baggageKey, String baggageValue) {
		for (Span span : this.actual.spans) {
			for (Map.Entry<String, String> baggage : span.baggageItems()) {
				if (baggage.getKey().equals(baggageKey)) {
					if (baggage.getValue().equals(baggageValue)) {
						return true;
					}
				}
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

	public ListOfSpansAssert hasASpanWithLogEqualTo(String logName) {
		isNotNull();
		printSpans();
		boolean found = false;
		for (Span span : this.actual.spans) {
			try {
				SleuthAssertions.assertThat(span).hasLoggedAnEvent(logName);
				found = true;
				break;
			} catch (AssertionError e) {}
		}

		if (!found) {
			failWithMessage("Expected spans \n <%s> \nto contain at least one span with log name "
					+ "equal to <%s>.\n\n", spansToString(), logName);
		}
		return this;
	}

	private String spansToString() {
		return this.actual.spans.stream().map(span ->  "\nSPAN: " + span.toString() + " with name [" + span.getName() + "] " +
				"\nwith tags " + span.tags() + "\nwith logs " + span.logs() +
				"\nwith baggage " + span.getBaggage()).collect(joining("\n"));
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

	private List<Span> findSpansWithSpanId(long spanId) {
		return this.actual.spans.stream()
				.filter(span -> spanId == span.getSpanId())
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

	public ListOfSpansAssert hasASpanWithSpanId(Long spanId) {
		isNotNull();
		printSpans();
		List<Span> matchingSpans = findSpansWithSpanId(spanId);
		if (matchingSpans.isEmpty()) {
			failWithMessage("Expected spans <%s> to contain a span with id <%s>", spansToString(), spanId);
		}
		return this;
	}

	public ListOfSpansAssert hasSize(int size) {
		isNotNull();
		printSpans();
		if (size != this.actual.spans.size()) {
			failWithMessage("Expected spans <%s> to be of size <%s> but was <%s>", spansToString(), size, actual.spans.size());
		}
		return this;
	}

	public ListOfSpansAssert hasRpcTagsInProperOrder() {
		isNotNull();
		printSpans();
		RpcLogKeeper rpcLogKeeper = findRpcLogs();
		log.info("Rpc logs [" + rpcLogKeeper.toString() + "]");
		rpcLogKeeper.assertThatAllBelongToSameTraceAndSpan();
		rpcLogKeeper.assertThatFullRpcCycleTookPlace();
		rpcLogKeeper.assertThatRpcLogsTookPlaceInOrder();
		return this;
	}

	public ListOfSpansAssert hasServerSideSpansInProperOrder() {
		isNotNull();
		printSpans();
		RpcLogKeeper rpcLogKeeper = findRpcLogs();
		log.info("Rpc logs [" + rpcLogKeeper.toString() + "]");
		rpcLogKeeper.assertThatServerSideEventsBelongToSameTraceAndSpan();
		rpcLogKeeper.assertThatServerSideEventsTookPlace();
		rpcLogKeeper.assertThatServerLogsTookPlaceInOrder();
		return this;
	}

	public ListOfSpansAssert hasRpcWithoutSeverSideDueToException() {
		isNotNull();
		printSpans();
		RpcLogKeeper rpcLogKeeper = findRpcLogs();
		log.info("Rpc logs [" + rpcLogKeeper.toString() + "]");
		rpcLogKeeper.assertThatClientSideEventsBelongToSameTraceAndSpan();
		rpcLogKeeper.assertThatClientSideEventsTookPlace();
		rpcLogKeeper.assertThatClientLogsTookPlaceInOrder();
		return this;
	}

	private void printSpans() {
		log.info("Stored spans " + spansToString());
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
				rpcLogKeeper.csSpanId = span.getSpanId();
				rpcLogKeeper.csTraceId = span.getTraceId();
				break;
			case Span.SERVER_RECV:
				rpcLogKeeper.sr = log;
				rpcLogKeeper.srSpanId = span.getSpanId();
				rpcLogKeeper.srTraceId = span.getTraceId();
				break;
			case Span.SERVER_SEND:
				rpcLogKeeper.ss = log;
				rpcLogKeeper.ssSpanId = span.getSpanId();
				rpcLogKeeper.ssTraceId = span.getTraceId();
				break;
			case Span.CLIENT_RECV:
				rpcLogKeeper.cr = log;
				rpcLogKeeper.crSpanId = span.getSpanId();
				rpcLogKeeper.crTraceId = span.getTraceId();
				break;
			default:
				break;
			}
		}));
		return rpcLogKeeper;
	}
}

class RpcLogKeeper {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	org.springframework.cloud.sleuth.Log cs;
	long csSpanId;
	long csTraceId;
	org.springframework.cloud.sleuth.Log sr;
	long srSpanId;
	long srTraceId;
	org.springframework.cloud.sleuth.Log ss;
	long ssSpanId;
	long ssTraceId;
	org.springframework.cloud.sleuth.Log cr;
	long crSpanId;
	long crTraceId;

	void assertThatFullRpcCycleTookPlace() {
		assertThatServerSideEventsTookPlace();
		assertThatClientSideEventsTookPlace();
	}
	void assertThatServerSideEventsTookPlace() {
		log.info("Checking if Server Received took place");
		assertThat(this.sr).describedAs("Server Received log").isNotNull();
		log.info("Checking if Server Send took place");
		assertThat(this.ss).describedAs("Server Send log").isNotNull();
		log.info("Checking if Client Received took place");
	}

	void assertThatClientSideEventsTookPlace() {
		log.info("Checking if Client Send took place");
		assertThat(this.cs).describedAs("Client Send log").isNotNull();
		log.info("Checking if Client Received took place");
		assertThat(this.cr).describedAs("Client Received log").isNotNull();
	}

	void assertThatAllBelongToSameTraceAndSpan() {
		log.info("Checking if RPC spans are coming from the same span");
		assertThat(this.csSpanId).describedAs("All logs should come from the same span")
				.isEqualTo(this.srSpanId).isEqualTo(this.ssSpanId).isEqualTo(this.crSpanId);
		log.info("Checking if RPC spans have the same trace id");
		assertThat(this.csTraceId).describedAs("All logs should come from the same trace")
				.isEqualTo(this.srTraceId).isEqualTo(this.ssTraceId).isEqualTo(this.crTraceId);
	}

	void assertThatClientSideEventsBelongToSameTraceAndSpan() {
		log.info("Checking if CR/CS logs are coming from the same span");
		assertThat(this.csSpanId).describedAs("All logs should come from the same span").isEqualTo(this.crSpanId);
		log.info("Checking if CR/CS logs have the same trace id");
		assertThat(this.csTraceId).describedAs("All logs should come from the same trace").isEqualTo(this.crTraceId);
	}

	void assertThatServerSideEventsBelongToSameTraceAndSpan() {
		log.info("Checking if SS/SR logs are coming from the same span");
		assertThat(this.ssSpanId).describedAs("All logs should come from the same span").isEqualTo(this.srSpanId);
		log.info("Checking if SS/SR logs have the same trace id");
		assertThat(this.ssTraceId).describedAs("All logs should come from the same trace").isEqualTo(this.srTraceId);
	}

	void assertThatRpcLogsTookPlaceInOrder() {
		long csTimestamp = this.cs.getTimestamp();
		long srTimestamp = this.sr.getTimestamp();
		long ssTimestamp = this.ss.getTimestamp();
		long crTimestamp = this.cr.getTimestamp();
		log.info("Checking if CR is before SR");
		assertThat(csTimestamp).as("CS timestamp should be before SR timestamp").isLessThanOrEqualTo(srTimestamp);
		log.info("Checking if SR is before SS");
		assertThat(srTimestamp).as("SR timestamp should be before SS timestamp").isLessThanOrEqualTo(ssTimestamp);
		log.info("Checking if SS is before CR");
		assertThat(ssTimestamp).as("SS timestamp should be before CR timestamp").isLessThanOrEqualTo(crTimestamp);
	}

	void assertThatClientLogsTookPlaceInOrder() {
		long csTimestamp = this.cs.getTimestamp();
		long crTimestamp = this.cr.getTimestamp();
		log.info("Checking if CS is before CR");
		assertThat(csTimestamp).as("CS timestamp should be before CR timestamp").isLessThanOrEqualTo(crTimestamp);
	}

	void assertThatServerLogsTookPlaceInOrder() {
		long srTimestamp = this.sr.getTimestamp();
		long ssTimestamp = this.ss.getTimestamp();
		log.info("Checking if CS is before CR");
		assertThat(srTimestamp).as("SR timestamp should be before SS timestamp").isLessThanOrEqualTo(ssTimestamp);
	}

	@Override public String toString() {
		return "RpcLogKeeper{" + "cs=" + cs + ", csSpanId=" + csSpanId + ", csTraceId="
				+ csTraceId + ", sr=" + sr + ", srSpanId=" + srSpanId + ", srTraceId="
				+ srTraceId + ", ss=" + ss + ", ssSpanId=" + ssSpanId + ", ssTraceId="
				+ ssTraceId + ", cr=" + cr + ", crSpanId=" + crSpanId + ", crTraceId="
				+ crTraceId + '}';
	}
}