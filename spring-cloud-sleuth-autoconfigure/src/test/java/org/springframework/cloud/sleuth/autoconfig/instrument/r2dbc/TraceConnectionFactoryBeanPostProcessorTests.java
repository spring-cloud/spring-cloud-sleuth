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

package org.springframework.cloud.sleuth.autoconfig.instrument.r2dbc;

import io.r2dbc.spi.ConnectionFactory;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.beans.factory.support.StaticListableBeanFactory;

class TraceConnectionFactoryBeanPostProcessorTests {

	@Test
	void should_do_nothing_when_bean_not_connection_factory() {
		TraceConnectionFactoryBeanPostProcessor processor = new TraceConnectionFactoryBeanPostProcessor(null) {
			@Override
			ConnectionFactory wrapConnectionFactory(ConnectionFactory bean) {
				throw new AssertionError("This method must not be called");
			}
		};
		Object before = new Object();

		Object after = processor.postProcessAfterInitialization(before, "");

		BDDAssertions.then(after).isSameAs(before);
	}

	@Test
	void should_modify_the_connection_factory() {
		TraceConnectionFactoryBeanPostProcessor processor = new TraceConnectionFactoryBeanPostProcessor(
				new StaticListableBeanFactory());
		ConnectionFactory before = BDDMockito.mock(ConnectionFactory.class);

		Object after = processor.postProcessAfterInitialization(before, "");

		BDDAssertions.then(after).isNotSameAs(before);
	}

}
