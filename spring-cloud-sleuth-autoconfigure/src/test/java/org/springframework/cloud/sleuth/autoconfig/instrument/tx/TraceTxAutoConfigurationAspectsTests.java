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

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.tx.TracePlatformTransactionManagerAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.sleuth.noop.enabled=true",
		classes = TraceTxAutoConfigurationAspectsTests.Config.class)
class TraceTxAutoConfigurationAspectsTests {

	@Autowired
	MyPlatformTransactionManager myPlatformTransactionManager;

	@Autowired
	TestPlatformAspect testPlatformAspect;

	@Test
	void should_make_aspects_work_for_platform() {
		myPlatformTransactionManager.getTransaction(null);
		assertThat(testPlatformAspect.getTransactionCalled).isTrue();

		myPlatformTransactionManager.commit(null);
		assertThat(testPlatformAspect.commitCalled).isTrue();

		myPlatformTransactionManager.rollback(null);
		assertThat(testPlatformAspect.rollbackCalled).isTrue();
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration({ TraceNoOpAutoConfiguration.class, TraceTxAutoConfiguration.class,
			AopAutoConfiguration.class })
	static class Config {

		@Bean
		MyPlatformTransactionManager myPlatformTransactionManager() {
			return new MyPlatformTransactionManager();
		}

		@Bean
		TestPlatformAspect testPlatformAspect(Tracer tracer, BeanFactory beanFactory) {
			return new TestPlatformAspect(tracer, beanFactory);
		}

	}

	static class TestPlatformAspect extends TracePlatformTransactionManagerAspect {

		boolean commitCalled;

		boolean rollbackCalled;

		boolean getTransactionCalled;

		TestPlatformAspect(Tracer tracer, BeanFactory beanFactory) {
			super(tracer, beanFactory);
		}

		@Override
		public Object traceCommit(ProceedingJoinPoint pjp, PlatformTransactionManager manager) {
			this.commitCalled = true;
			return null;
		}

		@Override
		public Object traceRollback(ProceedingJoinPoint pjp, PlatformTransactionManager manager) {
			this.rollbackCalled = true;
			return null;
		}

		@Override
		public Object traceGetTransaction(ProceedingJoinPoint pjp, PlatformTransactionManager manager) {
			this.getTransactionCalled = true;
			return null;
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

}
