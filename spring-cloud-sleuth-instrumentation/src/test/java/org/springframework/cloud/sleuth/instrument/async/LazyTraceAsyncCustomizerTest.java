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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Executor;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * @author Marcin Grzejszczak
 */
@ExtendWith(MockitoExtension.class)
public class LazyTraceAsyncCustomizerTest {

	@Mock
	BeanFactory beanFactory;

	@Mock
	AsyncConfigurer asyncConfigurer;

	@InjectMocks
	LazyTraceAsyncCustomizer lazyTraceAsyncCustomizer;

	@Test
	public void should_wrap_async_executor_in_trace_version() throws Exception {
		Executor executor = this.lazyTraceAsyncCustomizer.getAsyncExecutor();

		BDDAssertions.then(executor).isExactlyInstanceOf(LazyTraceExecutor.class);
	}

}
