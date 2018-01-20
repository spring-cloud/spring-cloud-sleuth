/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Executor;

import org.apache.commons.configuration.beanutils.BeanFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class LazyTraceAsyncCustomizerTest {

	@Mock BeanFactory beanFactory;
	@Mock AsyncConfigurer asyncConfigurer;
	@InjectMocks LazyTraceAsyncCustomizer lazyTraceAsyncCustomizer;

	@Test
	public void should_wrap_async_executor_in_trace_version() throws Exception {
		Executor executor = this.lazyTraceAsyncCustomizer.getAsyncExecutor();

		then(executor).isExactlyInstanceOf(LazyTraceExecutor.class);
	}
}