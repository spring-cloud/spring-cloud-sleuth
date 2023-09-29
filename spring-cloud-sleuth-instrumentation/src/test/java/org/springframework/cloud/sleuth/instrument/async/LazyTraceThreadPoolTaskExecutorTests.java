/*
 * Copyright 2013-2023 the original author or authors.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
public class LazyTraceThreadPoolTaskExecutorTests {

	private static final String BEAN_NAME = "mock";

	@Mock(lenient = true)
	private BeanFactory beanFactory;

	@Mock(lenient = true)
	private ThreadPoolTaskExecutor delegate;

	private LazyTraceThreadPoolTaskExecutor executor;

	@BeforeEach
	public void setup() {
		this.executor = spy(new LazyTraceThreadPoolTaskExecutor(beanFactory, delegate, BEAN_NAME));
	}

	@Test
	public void should_delegate_getQueueSize() {
		final int expected = 1000;
		doReturn(expected).when(delegate).getQueueSize();

		final int actual = executor.getQueueSize();

		assertThat(actual).isEqualTo(expected);
	}

}
