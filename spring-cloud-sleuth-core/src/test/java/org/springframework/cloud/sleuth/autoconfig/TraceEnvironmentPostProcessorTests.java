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

package org.springframework.cloud.sleuth.autoconfig;

import org.junit.jupiter.api.Test;

import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.BDDAssertions.then;

class TraceEnvironmentPostProcessorTests {

	MockEnvironment mockEnvironment = new MockEnvironment();

	TraceEnvironmentPostProcessor processor = new TraceEnvironmentPostProcessor();

	@Test
	void should_add_logging_pattern_when_not_disabled_explicitly() {

		this.processor.postProcessEnvironment(this.mockEnvironment, null);

		then(this.mockEnvironment.getProperty("logging.pattern.level")).isNotBlank();
	}

	@Test
	void should_not_add_logging_pattern_when_sleuth_disabled() {
		this.mockEnvironment.setProperty("spring.sleuth.enabled", "false");

		this.processor.postProcessEnvironment(this.mockEnvironment, null);

		then(this.mockEnvironment.getProperty("logging.pattern.level")).isBlank();

	}

	@Test
	void should_not_add_logging_pattern_when_sleuth_default_logging_pattern_disabled() {
		this.mockEnvironment.setProperty("spring.sleuth.default-logging-pattern-enabled",
				"false");

		this.processor.postProcessEnvironment(this.mockEnvironment, null);

		then(this.mockEnvironment.getProperty("logging.pattern.level")).isBlank();
	}

}
