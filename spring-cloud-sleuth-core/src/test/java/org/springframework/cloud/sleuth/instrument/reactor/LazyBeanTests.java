/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.reactor;

import brave.propagation.CurrentTraceContext;
import org.junit.Test;

import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LazyBeanTests {

	@Test
	public void should_return_null_when_exception_thrown_upon_bean_retrieval() {
		ConfigurableApplicationContext springContext = mock(
				ConfigurableApplicationContext.class);

		when(springContext.getBean(CurrentTraceContext.class))
				.thenThrow(new IllegalStateException());

		LazyBean<CurrentTraceContext> provider = new LazyBean<>(springContext,
				CurrentTraceContext.class);

		then(provider.get()).isNull();
	}

}
