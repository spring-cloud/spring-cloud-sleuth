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

package org.springframework.cloud.sleuth.exporter;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import static org.assertj.core.api.BDDAssertions.then;

class SpanIgnoringSpanFilterTests {

	private FinishedSpan namedSpan() {
		FinishedSpan span = BDDMockito.mock(FinishedSpan.class);
		BDDMockito.given(span.getName()).willReturn("someName");
		return span;
	}

	@Test
	void should_not_handle_span_when_present_in_main_list_of_spans_to_skip() {
		SpanIgnoringSpanFilter handler = new SpanIgnoringSpanFilter(Collections.singletonList("someName"),
				Collections.emptyList());

		then(handler.isExportable(namedSpan())).isFalse();
	}

	@Test
	void should_not_handle_span_when_present_in_additional_list_of_spans_to_skip() {
		SpanIgnoringSpanFilter handler = new SpanIgnoringSpanFilter(Collections.emptyList(),
				Collections.singletonList("someName"));

		then(handler.isExportable(namedSpan())).isFalse();
	}

	@Test
	void should_use_cached_entry_for_same_patterns() {
		export(handler("someOtherName"));
		export(handler("someOtherName"));
		export(handler("someOtherName"));

		then(SpanIgnoringSpanFilter.cache).containsKey("someOtherName");

		export(handler("a"));
		export(handler("b"));
		export(handler("c"));

		then(SpanIgnoringSpanFilter.cache).containsKey("someOtherName").containsKey("a").containsKey("b")
				.containsKey("c");
	}

	private void export(SpanIgnoringSpanFilter handler) {
		handler.isExportable(namedSpan());
	}

	private SpanIgnoringSpanFilter handler(String name) {
		return new SpanIgnoringSpanFilter(Collections.emptyList(), Collections.singletonList(name));
	}

}
