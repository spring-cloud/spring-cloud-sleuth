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

package org.springframework.cloud.sleuth.autoconfig.instrument.web;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenNoException;

@ExtendWith(OutputCaptureExtension.class)
class SkipPatternConfigurationTests {

	@Test
	void should_print_an_exception_when_bean_creation_exception_thrown(CapturedOutput output) {
		SkipPatternConfiguration configuration = new SkipPatternConfiguration();

		thenNoException().isThrownBy(() -> configuration.sleuthSkipPatternProvider(Collections.singletonList(() -> {
			throw new BeanCreationException("BOOM!");
		})));
		then(output.toString()).contains(
				"Most likely, there is an actuator endpoint that indirectly references an instrumented HTTP client")
				.contains("org.springframework.beans.factory.BeanCreationException: BOOM!");
	}

}
