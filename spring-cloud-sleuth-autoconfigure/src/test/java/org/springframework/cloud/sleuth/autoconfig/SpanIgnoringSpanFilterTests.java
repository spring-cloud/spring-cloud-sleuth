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

package org.springframework.cloud.sleuth.autoconfig;

import java.util.Collections;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;

import static org.assertj.core.api.BDDAssertions.then;

class SpanIgnoringSpanFilterTests {

	private FinishedSpan namedSpan() {
		FinishedSpan span = BDDMockito.mock(FinishedSpan.class);
		BDDMockito.given(span.getName()).willReturn("someName");
		return span;
	}

	@Test
	void should_not_handle_span_when_present_in_main_list_of_spans_to_skip() {
		SleuthSpanFilterProperties SleuthSpanFilterProperties = new SleuthSpanFilterProperties();
		SleuthSpanFilterProperties.setSpanNamePatternsToSkip(Collections.singletonList("someName"));
		SpanIgnoringSpanFilter handler = new SpanIgnoringSpanFilter(SleuthSpanFilterProperties);

		then(handler.isExportable(namedSpan())).isFalse();
	}

	@Test
	void should_not_handle_span_when_present_in_additional_list_of_spans_to_skip() {
		SleuthSpanFilterProperties SleuthSpanFilterProperties = SleuthSpanExporterPropertiesWithAdditionalEntries();
		SpanIgnoringSpanFilter handler = new SpanIgnoringSpanFilter(SleuthSpanFilterProperties);

		then(handler.isExportable(namedSpan())).isFalse();
	}

	@Test
	void should_use_cached_entry_for_same_patterns() {
		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("someOtherName")));
		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("someOtherName")));
		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("someOtherName")));

		then(SpanIgnoringSpanFilter.cache).containsKey("someOtherName");

		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("a")));
		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("b")));
		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("c")));

		then(SpanIgnoringSpanFilter.cache).containsKey("someOtherName").containsKey("a").containsKey("b")
				.containsKey("c");
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(TraceConfiguration.class);

	@Test
	void should_not_register_span_handler_when_property_passed() {
		this.contextRunner.withPropertyValues("spring.sleuth.span-filter.enabled=false")
				.run((context) -> BDDAssertions.thenThrownBy(() -> context.getBean(SpanIgnoringSpanFilter.class))
						.isInstanceOf(NoSuchBeanDefinitionException.class));
	}

	@Test
	void should_register_span_handler_by_default() {
		this.contextRunner.run((context) -> context.getBean(SpanIgnoringSpanFilter.class));
	}

	private SleuthSpanFilterProperties SleuthSpanExporterPropertiesWithAdditionalEntries() {
		return SleuthSpanExporterPropertiesWithAdditionalEntries("someName");
	}

	private SleuthSpanFilterProperties SleuthSpanExporterPropertiesWithAdditionalEntries(String name) {
		SleuthSpanFilterProperties SleuthSpanFilterProperties = new SleuthSpanFilterProperties();
		SleuthSpanFilterProperties.setAdditionalSpanNamePatternsToIgnore(Collections.singletonList(name));
		return SleuthSpanFilterProperties;
	}

	private void export(SpanIgnoringSpanFilter handler) {
		handler.isExportable(namedSpan());
	}

	private SpanIgnoringSpanFilter handler(SleuthSpanFilterProperties SleuthSpanFilterProperties) {
		return new SpanIgnoringSpanFilter(SleuthSpanFilterProperties);
	}

}
