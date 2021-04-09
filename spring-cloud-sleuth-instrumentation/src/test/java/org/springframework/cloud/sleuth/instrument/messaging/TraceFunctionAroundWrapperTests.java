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

package org.springframework.cloud.sleuth.instrument.messaging;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;

class TraceFunctionAroundWrapperTests {

	@Test
	void should_clear_cache_on_refresh() {
		TraceFunctionAroundWrapper wrapper = new TraceFunctionAroundWrapper(null, null, null, null, null);
		wrapper.functionToDestinationCache.put("example", "entry");
		then(wrapper.functionToDestinationCache).isNotEmpty();

		wrapper.onApplicationEvent(null);

		then(wrapper.functionToDestinationCache).isEmpty();
	}

	@Test
	void test_with_standard_bindings() throws Exception {
		try (GenericApplicationContext context = new GenericApplicationContext()) {
			System.setProperty("spring.cloud.stream.bindings.marcin-in-0.destination", "oleg");
			System.setProperty("spring.cloud.stream.bindings.marcin-out-0.destination", "bob");
			TraceFunctionAroundWrapper wrapper = new TraceFunctionAroundWrapper(context.getEnvironment(), null, null,
					null, null);

			Method inputDestinationMethod = ReflectionUtils.findMethod(TraceFunctionAroundWrapper.class,
					"inputDestination", String.class);
			inputDestinationMethod.setAccessible(true);
			assertThat(inputDestinationMethod.invoke(wrapper, "marcin")).isEqualTo("oleg"); // gross
																							// overestimation
																							// ;)

			wrapper.functionToDestinationCache.clear();
			Method outputDestinationMethod = ReflectionUtils.findMethod(TraceFunctionAroundWrapper.class,
					"outputDestination", String.class);
			outputDestinationMethod.setAccessible(true);
			assertThat(outputDestinationMethod.invoke(wrapper, "marcin")).isEqualTo("bob");
		}
	}

	@Test
	void test_with_remapped_bindings() throws Exception {
		try (GenericApplicationContext context = new GenericApplicationContext()) {
			System.setProperty("spring.cloud.stream.function.bindings.marcin-in-0", "input");
			System.setProperty("spring.cloud.stream.bindings.input.destination", "oleg");
			System.setProperty("spring.cloud.stream.function.bindings.marcin-out-0", "output");
			System.setProperty("spring.cloud.stream.bindings.output.destination", "bob");
			TraceFunctionAroundWrapper wrapper = new TraceFunctionAroundWrapper(context.getEnvironment(), null, null,
					null, null);

			Method inputDestinationMethod = ReflectionUtils.findMethod(TraceFunctionAroundWrapper.class,
					"inputDestination", String.class);
			inputDestinationMethod.setAccessible(true);
			assertThat(inputDestinationMethod.invoke(wrapper, "marcin")).isEqualTo("oleg"); // that's
																							// a
																							// gross
																							// overestimation
																							// ;)

			wrapper.functionToDestinationCache.clear();
			Method outputDestinationMethod = ReflectionUtils.findMethod(TraceFunctionAroundWrapper.class,
					"outputDestination", String.class);
			outputDestinationMethod.setAccessible(true);
			assertThat(outputDestinationMethod.invoke(wrapper, "marcin")).isEqualTo("bob");
		}
	}

}
