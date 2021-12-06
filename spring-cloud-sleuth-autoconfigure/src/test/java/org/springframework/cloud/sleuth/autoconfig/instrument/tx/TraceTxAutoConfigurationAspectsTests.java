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

package org.springframework.cloud.sleuth.autoconfig.instrument.tx;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(properties = "spring.sleuth.noop.enabled=true",
		classes = TraceTxAutoConfigurationAspectsTests.Config.class)
class TraceTxAutoConfigurationAspectsTests {

	@Autowired
	MyReactiveTransactionManager myReactiveTransactionManager;

	@Autowired
	MyPlatformTransactionManager myPlatformTransactionManager;

	@Test
	void should_make_proxies_work_for_platform() {
		then(this.myReactiveTransactionManager).isNotNull().isInstanceOf(MyReactiveTransactionManager.class);
		then(this.myPlatformTransactionManager).isNotNull().isInstanceOf(MyPlatformTransactionManager.class);
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration({ TraceNoOpAutoConfiguration.class, TraceTxAutoConfiguration.class,
			AopAutoConfiguration.class })
	static class Config {

		@Bean
		MyReactiveTransactionManager myReactiveTransactionManager() {
			return new MyReactiveTransactionManager();
		}

		@Bean
		MyPlatformTransactionManager myPlatformTransactionManager() {
			return new MyPlatformTransactionManager();
		}

	}

	static class MyPlatformTransactionManager implements PlatformTransactionManager {

		@Override
		public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
			return null;
		}

		@Override
		public void commit(TransactionStatus status) throws TransactionException {

		}

		@Override
		public void rollback(TransactionStatus status) throws TransactionException {

		}

	}

	static class MyReactiveTransactionManager implements ReactiveTransactionManager {

		@Override
		public Mono<ReactiveTransaction> getReactiveTransaction(TransactionDefinition definition)
				throws TransactionException {
			return null;
		}

		@Override
		public Mono<Void> commit(ReactiveTransaction transaction) throws TransactionException {
			return null;
		}

		@Override
		public Mono<Void> rollback(ReactiveTransaction transaction) throws TransactionException {
			return null;
		}

	}

}
