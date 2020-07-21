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

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.beans.factory.BeanFactory;

public class LazyTraceScheduledThreadPoolExecutorTests {

	@Test
	public void should_not_finalize_the_delegate_since_its_a_shared_instance() {
		AtomicBoolean wasCalled = new AtomicBoolean();
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(10) {
			@Override
			protected void finalize() {
				super.finalize();
				wasCalled.set(true);
			}
		};
		BeanFactory beanFactory = BDDMockito.mock(BeanFactory.class);

		new LazyTraceScheduledThreadPoolExecutor(10, beanFactory, executor).finalize();

		BDDAssertions.then(wasCalled).isFalse();
		BDDAssertions.then(executor.isShutdown()).isFalse();
	}

}
