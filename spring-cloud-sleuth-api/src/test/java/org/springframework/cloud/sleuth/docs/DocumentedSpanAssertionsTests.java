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

package org.springframework.cloud.sleuth.docs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.cloud.sleuth.Span;

import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.springframework.cloud.sleuth.docs.DocumentedSpanAssertions.assertThatEventIsValid;
import static org.springframework.cloud.sleuth.docs.DocumentedSpanAssertions.assertThatKeyIsValid;
import static org.springframework.cloud.sleuth.docs.DocumentedSpanAssertions.assertThatNameIsValid;
import static org.springframework.cloud.sleuth.docs.DocumentedSpanAssertions.assertThatSpanStartedBeforeEnd;
import static org.springframework.cloud.sleuth.docs.DocumentedSpanAssertionsTests.MyEventsWithNotMatchingPrefix.A_BAR_EVENT;
import static org.springframework.cloud.sleuth.docs.DocumentedSpanAssertionsTests.MySpan.SPAN_WITH_DYNAMIC_ENTRIES;
import static org.springframework.cloud.sleuth.docs.DocumentedSpanAssertionsTests.MySpan.SPAN_WITH_EMPTY_TAGS_AND_EVENTS;
import static org.springframework.cloud.sleuth.docs.DocumentedSpanAssertionsTests.MySpan.SPAN_WITH_NOT_MATCHING_PREFIX;
import static org.springframework.cloud.sleuth.docs.DocumentedSpanAssertionsTests.MySpan.SPAN_WITH_PREFIX;
import static org.springframework.cloud.sleuth.docs.DocumentedSpanAssertionsTests.MyTags.A_FOO_TAG;

class DocumentedSpanAssertionsTests {

	@BeforeEach
	void setup() {
		DocumentedSpanAssertions.SLEUTH_SPAN_ASSERTIONS_ON = true;
	}

	@Test
	void should_do_nothing_when_system_property_not_turned_on() {
		DocumentedSpanAssertions.SLEUTH_SPAN_ASSERTIONS_ON = false;

		assertThatKeyIsValid("unknown_key", SPAN_WITH_NOT_MATCHING_PREFIX);
		assertThatKeyIsValid(A_FOO_TAG, SPAN_WITH_PREFIX);
		assertThatEventIsValid("unknown_event", SPAN_WITH_PREFIX);
		assertThatEventIsValid(A_BAR_EVENT, SPAN_WITH_PREFIX);
		assertThatNameIsValid("unknown_name", SPAN_WITH_NOT_MATCHING_PREFIX);
		assertThatSpanStartedBeforeEnd(
				new ImmutableAssertingSpan(SPAN_WITH_NOT_MATCHING_PREFIX, BDDMockito.mock(Span.class)));
	}

	@Test
	void should_do_nothing_when_tags_or_events_are_empty() {
		assertThatKeyIsValid("unknown_key", SPAN_WITH_EMPTY_TAGS_AND_EVENTS);
		assertThatKeyIsValid(A_FOO_TAG, SPAN_WITH_EMPTY_TAGS_AND_EVENTS);
		assertThatEventIsValid("unknown_event", SPAN_WITH_EMPTY_TAGS_AND_EVENTS);
		assertThatEventIsValid(A_BAR_EVENT, SPAN_WITH_EMPTY_TAGS_AND_EVENTS);
	}

	@Test
	void should_not_fail_when_keys_and_values_are_properly_prefixed() {
		assertThatKeyIsValid("some.key", SPAN_WITH_DYNAMIC_ENTRIES);
		assertThatKeyIsValid(String.format(MyDynamicTags.A_DYNAMIC_TAG.getKey(), "some"), SPAN_WITH_DYNAMIC_ENTRIES);
		assertThatEventIsValid("some.value", SPAN_WITH_DYNAMIC_ENTRIES);
		assertThatEventIsValid(String.format(MyDynamicEvents.A_DYNAMIC_EVENT.getValue(), "some"),
				SPAN_WITH_DYNAMIC_ENTRIES);
	}

	@Test
	void should_not_fail_when_span_was_started_and_then_ended() {
		assertThatSpanStartedBeforeEnd(
				new ImmutableAssertingSpan(SPAN_WITH_NOT_MATCHING_PREFIX, BDDMockito.mock(Span.class)).start());
	}

	@Test
	void should_fail_when_assertion_is_on_and_a_key_is_unknown() {
		thenThrownBy(() -> assertThatKeyIsValid("unknown_key", SPAN_WITH_NOT_MATCHING_PREFIX))
				.hasMessageContaining("The key [unknown_key] is invalid");
		thenThrownBy(() -> assertThatKeyIsValid(A_FOO_TAG, SPAN_WITH_NOT_MATCHING_PREFIX))
				.hasMessageContaining("The key [foo.key] is invalid");
	}

	@Test
	void should_fail_when_assertion_is_on_and_an_event_is_unknown() {
		thenThrownBy(() -> assertThatEventIsValid("unknown_event", SPAN_WITH_PREFIX))
				.hasMessageContaining("The event [unknown_event] is invalid");
		thenThrownBy(() -> assertThatEventIsValid(A_BAR_EVENT, SPAN_WITH_PREFIX))
				.hasMessageContaining("The event [bar.value] is invalid");
	}

	@Test
	void should_fail_when_assertion_is_on_and_a_key_is_known_but_wrongly_prefixed() {
		thenThrownBy(() -> assertThatKeyIsValid("bar.key", SPAN_WITH_NOT_MATCHING_PREFIX))
				.hasMessageContaining("Also it has start with [foo.] prefix");
		thenThrownBy(() -> assertThatKeyIsValid(MyTagsWithNotMatchingPrefix.A_BAR_TAG, SPAN_WITH_NOT_MATCHING_PREFIX))
				.hasMessageContaining("Also it has start with [foo.] prefix");
	}

	@Test
	void should_fail_when_assertion_is_on_and_an_event_is_known_but_wrongly_prefixed() {
		thenThrownBy(() -> assertThatEventIsValid("bar.value", SPAN_WITH_NOT_MATCHING_PREFIX))
				.hasMessageContaining("Also it has start with [foo.] prefix");
		thenThrownBy(() -> assertThatEventIsValid(A_BAR_EVENT, SPAN_WITH_NOT_MATCHING_PREFIX))
				.hasMessageContaining("Also it has start with [foo.] prefix");
	}

	@Test
	void should_fail_when_assertion_is_on_and_a_key_is_known_but_dynamic_key_is_not_matched() {
		thenThrownBy(() -> assertThatKeyIsValid("notmatching", SPAN_WITH_DYNAMIC_ENTRIES))
				.hasMessageContaining("The key [notmatching] is invalid. You can use only one matching [%s.key]");
		thenThrownBy(() -> assertThatKeyIsValid(MySimpleTag.A_SIMPLE_TAG, SPAN_WITH_DYNAMIC_ENTRIES))
				.hasMessageContaining("The key [simple] is invalid. You can use only one matching [%s.key]");
	}

	@Test
	void should_fail_when_assertion_is_on_and_an_event_is_known_but_dynamic_value_is_not_matched() {
		thenThrownBy(() -> assertThatEventIsValid("notmatching", SPAN_WITH_DYNAMIC_ENTRIES))
				.hasMessageContaining("The event [notmatching] is invalid. You can use only one matching [%s.value]");
		thenThrownBy(() -> assertThatEventIsValid(MySimpleEvent.A_SIMPLE_EVENT, SPAN_WITH_DYNAMIC_ENTRIES))
				.hasMessageContaining("The event [simple] is invalid. You can use only one matching [%s.value]");
	}

	@Test
	void should_fail_when_assertion_is_on_and_name_is_invalid() {
		thenThrownBy(() -> assertThatNameIsValid("unknown_name", SPAN_WITH_NOT_MATCHING_PREFIX))
				.hasMessageContaining("The name [unknown_name] is invalid");
	}

	@Test
	void should_fail_when_assertion_is_on_and_name_is_not_matching() {
		thenThrownBy(() -> assertThatNameIsValid("unknown_name", SPAN_WITH_DYNAMIC_ENTRIES)).hasMessageContaining(
				"The name [unknown_name] is invalid. You can use only one matching [%s somename]");
	}

	@Test
	void should_fail_when_span_was_ended_but_not_started() {
		thenThrownBy(() -> assertThatSpanStartedBeforeEnd(
				new ImmutableAssertingSpan(SPAN_WITH_NOT_MATCHING_PREFIX, BDDMockito.mock(Span.class))))
						.hasMessageContaining("The span was not started");
	}

	enum MySpan implements DocumentedSpan {

		SPAN_WITH_PREFIX {
			@Override
			public String getName() {
				return "foo";
			}

			@Override
			public TagKey[] getTagKeys() {
				return MyTags.values();
			}

			@Override
			public EventValue[] getEvents() {
				return MyEvents.values();
			}

			@Override
			public String prefix() {
				return "foo.";
			}
		},

		SPAN_WITH_NOT_MATCHING_PREFIX {
			@Override
			public String getName() {
				return "bar";
			}

			@Override
			public TagKey[] getTagKeys() {
				return MyTagsWithNotMatchingPrefix.values();
			}

			@Override
			public EventValue[] getEvents() {
				return MyEventsWithNotMatchingPrefix.values();
			}

			@Override
			public String prefix() {
				return "foo.";
			}
		},

		SPAN_WITH_EMPTY_TAGS_AND_EVENTS {
			@Override
			public String getName() {
				return "baz";
			}
		},

		SPAN_WITH_DYNAMIC_ENTRIES {
			@Override
			public String getName() {
				return "%s somename";
			}

			@Override
			public TagKey[] getTagKeys() {
				return MyDynamicTags.values();
			}

			@Override
			public EventValue[] getEvents() {
				return MyDynamicEvents.values();
			}
		}

	}

	enum MyTags implements TagKey {

		A_FOO_TAG {
			@Override
			public String getKey() {
				return "foo.key";
			}
		}

	}

	enum MyEvents implements EventValue {

		A_FOO_EVENT {
			@Override
			public String getValue() {
				return "foo.value";
			}
		}

	}

	enum MyTagsWithNotMatchingPrefix implements TagKey {

		A_BAR_TAG {
			@Override
			public String getKey() {
				return "bar.key";
			}
		}

	}

	enum MyEventsWithNotMatchingPrefix implements EventValue {

		A_BAR_EVENT {
			@Override
			public String getValue() {
				return "bar.value";
			}
		}

	}

	enum MySimpleTag implements TagKey {

		A_SIMPLE_TAG {
			@Override
			public String getKey() {
				return "simple";
			}
		}

	}

	enum MySimpleEvent implements EventValue {

		A_SIMPLE_EVENT {
			@Override
			public String getValue() {
				return "simple";
			}
		}

	}

	enum MyDynamicTags implements TagKey {

		A_DYNAMIC_TAG {
			@Override
			public String getKey() {
				return "%s.key";
			}
		}

	}

	enum MyDynamicEvents implements EventValue {

		A_DYNAMIC_EVENT {
			@Override
			public String getValue() {
				return "%s.value";
			}
		}

	}

}
