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

package org.springframework.cloud.sleuth.instrument.async;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * @author Marcin Grzejszczak
 */
public class TraceAsyncCustomAutoConfigurationTest {

	@Test
	public void should_return_bean_when_its_not_a_async_configurer() throws Exception {
		TraceAsyncCustomAutoConfiguration configuration = new TraceAsyncCustomAutoConfiguration();

		Object bean = configuration.postProcessAfterInitialization(new Object(), "someName");

		BDDAssertions.then(bean).isNotInstanceOf(LazyTraceAsyncCustomizer.class);
	}

	@Test
	public void should_return_lazy_async_configurer_when_bean_is_async_configurer() throws Exception {
		TraceAsyncCustomAutoConfiguration configuration = new TraceAsyncCustomAutoConfiguration();

		Object bean = configuration.postProcessAfterInitialization(Mockito.mock(AsyncConfigurer.class), "someName");

		BDDAssertions.then(bean).isInstanceOf(LazyTraceAsyncCustomizer.class);
	}

}
