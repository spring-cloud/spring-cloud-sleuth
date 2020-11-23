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

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.exporter.SpanIgnoringSpanFilter;

class SpanIgnoringSpanFilterTests {

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

}
