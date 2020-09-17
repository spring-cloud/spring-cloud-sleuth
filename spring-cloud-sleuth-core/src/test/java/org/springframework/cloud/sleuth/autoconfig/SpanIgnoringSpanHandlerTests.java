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

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.BDDAssertions.then;

class SpanIgnoringSpanHandlerTests {

	@Test
	void should_handle_span_when_not_yet_finished() {
		SpanIgnoringSpanHandler handler = new SpanIgnoringSpanHandler(new SleuthProperties());

		then(handler.end(null, null, SpanHandler.Cause.ABANDONED)).isTrue();
	}

	@Test
	void should_handle_span_when_name_null() {
		SpanIgnoringSpanHandler handler = new SpanIgnoringSpanHandler(new SleuthProperties());

		then(handler.end(null, new MutableSpan(), SpanHandler.Cause.FINISHED)).isTrue();
	}

	@Test
	void should_handle_span_when_not_present_in_main_list_of_spans_to_skip() {
		SpanIgnoringSpanHandler handler = new SpanIgnoringSpanHandler(new SleuthProperties());

		then(handler.end(null, namedSpan(), SpanHandler.Cause.FINISHED)).isTrue();
	}

	private MutableSpan namedSpan() {
		MutableSpan span = new MutableSpan();
		span.name("someName");
		return span;
	}

	@Test
	void should_not_handle_span_when_present_in_main_list_of_spans_to_skip() {
		SleuthProperties sleuthProperties = new SleuthProperties();
		sleuthProperties.getSpanHandler().setSpanNamePatternsToSkip(Collections.singletonList("someName"));
		SpanIgnoringSpanHandler handler = new SpanIgnoringSpanHandler(sleuthProperties);

		then(handler.end(null, namedSpan(), SpanHandler.Cause.FINISHED)).isFalse();
	}

	@Test
	void should_not_handle_span_when_present_in_additional_list_of_spans_to_skip() {
		SleuthProperties sleuthProperties = sleuthPropertiesWithAdditionalEntries();
		SpanIgnoringSpanHandler handler = new SpanIgnoringSpanHandler(sleuthProperties);

		then(handler.end(null, namedSpan(), SpanHandler.Cause.FINISHED)).isFalse();
	}

	@Test
	void should_use_cached_entry_for_same_patterns() {
		end(handler(sleuthPropertiesWithAdditionalEntries("someOtherName")));
		end(handler(sleuthPropertiesWithAdditionalEntries("someOtherName")));
		end(handler(sleuthPropertiesWithAdditionalEntries("someOtherName")));

		then(SpanIgnoringSpanHandler.cache).containsKey("someOtherName");

		end(handler(sleuthPropertiesWithAdditionalEntries("a")));
		end(handler(sleuthPropertiesWithAdditionalEntries("b")));
		end(handler(sleuthPropertiesWithAdditionalEntries("c")));

		then(SpanIgnoringSpanHandler.cache).containsKey("someOtherName").containsKey("a").containsKey("b")
				.containsKey("c");
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class));

	@Test
	void should_not_register_span_handler_when_property_passed() {
		this.contextRunner.withPropertyValues("spring.sleuth.span-handler.enabled=false")
				.run((context) -> BDDAssertions.thenThrownBy(() -> context.getBean(SpanIgnoringSpanHandler.class))
						.isInstanceOf(NoSuchBeanDefinitionException.class));
	}

	@Test
	void should_register_span_handler_by_default() {
		this.contextRunner.run((context) -> context.getBean(SpanIgnoringSpanHandler.class));
	}

	private SleuthProperties sleuthPropertiesWithAdditionalEntries() {
		return sleuthPropertiesWithAdditionalEntries("someName");
	}

	private SleuthProperties sleuthPropertiesWithAdditionalEntries(String name) {
		SleuthProperties sleuthProperties = new SleuthProperties();
		sleuthProperties.getSpanHandler().setAdditionalSpanNamePatternsToIgnore(Collections.singletonList(name));
		return sleuthProperties;
	}

	private void end(SpanIgnoringSpanHandler handler) {
		handler.end(null, namedSpan(), SpanHandler.Cause.FINISHED);
	}

	private SpanIgnoringSpanHandler handler(SleuthProperties sleuthProperties) {
		return new SpanIgnoringSpanHandler(sleuthProperties);
	}

}
