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
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.api.exporter.ReportedSpan;

import static org.assertj.core.api.BDDAssertions.then;

class SpanIgnoringSpanExporterTests {

	private ReportedSpan namedSpan() {
		ReportedSpan span = BDDMockito.mock(ReportedSpan.class);
		BDDMockito.given(span.name()).willReturn("someName");
		return span;
	}

	@Test
	void should_not_handle_span_when_present_in_main_list_of_spans_to_skip() {
		SleuthSpanExporterProperties SleuthSpanExporterProperties = new SleuthSpanExporterProperties();
		SleuthSpanExporterProperties.setSpanNamePatternsToSkip(Collections.singletonList("someName"));
		SpanIgnoringSpanExporter handler = new SpanIgnoringSpanExporter(SleuthSpanExporterProperties);

		then(handler.export(namedSpan())).isFalse();
	}

	@Test
	void should_not_handle_span_when_present_in_additional_list_of_spans_to_skip() {
		SleuthSpanExporterProperties SleuthSpanExporterProperties = SleuthSpanExporterPropertiesWithAdditionalEntries();
		SpanIgnoringSpanExporter handler = new SpanIgnoringSpanExporter(SleuthSpanExporterProperties);

		then(handler.export(namedSpan())).isFalse();
	}

	@Test
	void should_use_cached_entry_for_same_patterns() {
		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("someOtherName")));
		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("someOtherName")));
		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("someOtherName")));

		then(SpanIgnoringSpanExporter.cache).containsKey("someOtherName");

		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("a")));
		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("b")));
		export(handler(SleuthSpanExporterPropertiesWithAdditionalEntries("c")));

		then(SpanIgnoringSpanExporter.cache).containsKey("someOtherName").containsKey("a").containsKey("b")
				.containsKey("c");
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class));

	@Test
	void should_not_register_span_handler_when_property_passed() {
		this.contextRunner.withPropertyValues("spring.sleuth.span-exporter.enabled=false")
				.run((context) -> BDDAssertions.thenThrownBy(() -> context.getBean(SpanIgnoringSpanExporter.class))
						.isInstanceOf(NoSuchBeanDefinitionException.class));
	}

	@Test
	void should_register_span_handler_by_default() {
		this.contextRunner.run((context) -> context.getBean(SpanIgnoringSpanExporter.class));
	}

	private SleuthSpanExporterProperties SleuthSpanExporterPropertiesWithAdditionalEntries() {
		return SleuthSpanExporterPropertiesWithAdditionalEntries("someName");
	}

	private SleuthSpanExporterProperties SleuthSpanExporterPropertiesWithAdditionalEntries(String name) {
		SleuthSpanExporterProperties SleuthSpanExporterProperties = new SleuthSpanExporterProperties();
		SleuthSpanExporterProperties.setAdditionalSpanNamePatternsToIgnore(Collections.singletonList(name));
		return SleuthSpanExporterProperties;
	}

	private void export(SpanIgnoringSpanExporter handler) {
		handler.export(namedSpan());
	}

	private SpanIgnoringSpanExporter handler(SleuthSpanExporterProperties SleuthSpanExporterProperties) {
		return new SpanIgnoringSpanExporter(SleuthSpanExporterProperties);
	}

}
