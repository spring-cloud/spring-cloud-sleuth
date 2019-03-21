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

import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.springframework.cloud.sleuth.Span;

public class SpanAssert extends AbstractAssert<SpanAssert, Span> {

	private static final Log log = LogFactory.getLog(SpanAssert.class);

	public SpanAssert(Span actual) {
		super(actual, SpanAssert.class);
	}

	public static SpanAssert then(Span actual) {
		return new SpanAssert(actual);
	}

	public SpanAssert hasTraceIdEqualTo(Long traceId) {
		isNotNull();
		if (!Objects.equals(this.actual.getTraceId(), traceId)) {
			String message = String.format("Expected span's traceId to be <%s> but was <%s>", traceId, this.actual.getTraceId());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasParentSpanIdEqualTo(Long spanId) {
		isNotNull();
		Assertions.assertThat(this.actual.getParents()).isNotEmpty();
		Long parentSpanId = this.actual.getParents().get(0);
		if (!Objects.equals(parentSpanId, spanId)) {
			String message = String.format("Expected span's parent spanId to be <%s> but was <%s>", spanId, parentSpanId);
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasSpanIdEqualTo(Long spanId) {
		isNotNull();
		if (!Objects.equals(this.actual.getSpanId(), spanId)) {
			String message = String.format("Expected span's spanId to be <%s> but was <%s>", spanId, this.actual.getSpanId());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasNameEqualTo(String name) {
		isNotNull();
		if (!Objects.equals(this.actual.getName(), name)) {
			String message = String.format("Expected span's name to be <%s> but it was <%s>", name, this.actual.getName());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert nameStartsWith(String string) {
		isNotNull();
		if (!this.actual.getName().startsWith(string)) {
			String message = String.format("Expected span's name to start with <%s> but it was equal to <%s>", string, this.actual.getName());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasNameNotEqualTo(String name) {
		isNotNull();
		if (Objects.equals(this.actual.getName(), name)) {
			String message = String.format("Expected span's name NOT to be <%s> but it was <%s>", name, this.actual.getName());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert isALocalComponentSpan() {
		isNotNull();
		if (!this.actual.tags().containsKey(Span.SPAN_LOCAL_COMPONENT_TAG_NAME)) {
			String message = String.format("Expected span to be a local component. "
					+ "LC tag is missing. Found tags are <%s>", this.actual.tags());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasATag(String tagKey, String tagValue) {
		isNotNull();
		assertThatTagIsPresent(tagKey);
		String foundTagValue = this.actual.tags().get(tagKey);
		if (!foundTagValue.equals(tagValue)) {
			String message = String.format("Expected span to have the tag with key <%s> and value <%s>. "
					+ "Found value for that tag is <%s>", tagKey, tagValue, foundTagValue);
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasBaggageItem(String baggageKey, String baggageValue) {
		isNotNull();
		assertThatBaggageContainsKey(baggageKey);
		String foundValue = this.actual.getBaggageItem(baggageKey);
		if (!foundValue.equals(baggageValue)) {
			String message = String.format("Expected span to have the baggage with key <%s> and value <%s>. "
					+ "Found value for that baggage is <%s>", baggageKey, baggageValue, foundValue);
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasATagWithKey(String tagKey) {
		isNotNull();
		assertThatTagIsPresent(tagKey);
		boolean foundTagValue = this.actual.tags().containsKey(tagKey);
		if (!foundTagValue) {
			String message = String.format("Expected span to have the tag with key <%s>. "
					+ "Found tags are <%s>", tagKey, this.actual.tags());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert matchesATag(String tagKey, String tagRegex) {
		isNotNull();
		assertThatTagIsPresent(tagKey);
		String foundTagValue = this.actual.tags().get(tagKey);
		if (!foundTagValue.matches(tagRegex)) {
			String message = String.format("Expected span to have the tag with key <%s> and match a regex <%s>. "
					+ "Found value for that tag is <%s>", tagKey, tagRegex, foundTagValue);
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	private void assertThatTagIsPresent(String tagKey) {
		if (!this.actual.tags().containsKey(tagKey)) {
			String message = String.format("Expected span to have the tag with key <%s>. "
					+ "Found tags are <%s>", tagKey, this.actual.tags());
			log.error(message);
			failWithMessage(message);
		}
	}

	private void assertThatBaggageContainsKey(String baggageKey) {
		if (!this.actual.getBaggage().containsKey(baggageKey)) {
			String message = String.format("Expected span to have the baggage with key <%s>. "
					+ "Found baggage are <%s>", baggageKey, this.actual.getBaggage());
			log.error(message);
			failWithMessage(message);
		}
	}

	public SpanAssert hasLoggedAnEvent(String event) {
		isNotNull();
		if (!this.actual.logs().stream().map(org.springframework.cloud.sleuth.Log::getEvent)
				.anyMatch(s -> s.equals(event))) {
			String message = String.format("Expected span to have the event with event value <%s>. "
					+ "Found logs are <%s>", event, this.actual.logs());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasNotLoggedAnEvent(String event) {
		isNotNull();
		if (this.actual.logs().stream().map(org.springframework.cloud.sleuth.Log::getEvent)
				.anyMatch(s -> s.equals(event))) {
			String message = String.format("Expected span NOT to have the event with event value <%s>. "
					+ "Found logs are <%s>", event, this.actual.logs());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert isExportable() {
		isNotNull();
		if (!this.actual.isExportable()) {
			String message = "The span is supposed to be exportable but it's not!";
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert isShared() {
		isNotNull();
		if (!this.actual.isShared()) {
			String message = "The span is supposed to be shared but it's not!";
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert isNotShared() {
		isNotNull();
		if (this.actual.isShared()) {
			String message = "The span is NOT supposed to be shared but it is!";
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert isNotExportable() {
		isNotNull();
		if (this.actual.isExportable()) {
			String message = "The span is NOT supposed to be exportable but it is!";
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}
}