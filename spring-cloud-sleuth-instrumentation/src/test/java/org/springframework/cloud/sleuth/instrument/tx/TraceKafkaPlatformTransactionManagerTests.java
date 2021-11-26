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

package org.springframework.cloud.sleuth.instrument.tx;

import org.mockito.Mockito;

import org.springframework.kafka.transaction.KafkaAwareTransactionManager;

class TraceKafkaPlatformTransactionManagerTests extends TracePlatformTransactionManagerTests {

	KafkaAwareTransactionManager delegate = Mockito.mock(KafkaAwareTransactionManager.class);

	@Override
	TraceKafkaAwareTransactionManager manager() {
		final TraceKafkaAwareTransactionManager manager = new TraceKafkaAwareTransactionManager(getDelegate(),
				beanFactory());
		manager.initialize();
		return manager;
	}

	@Override
	KafkaAwareTransactionManager getDelegate() {
		return this.delegate;
	}

}
