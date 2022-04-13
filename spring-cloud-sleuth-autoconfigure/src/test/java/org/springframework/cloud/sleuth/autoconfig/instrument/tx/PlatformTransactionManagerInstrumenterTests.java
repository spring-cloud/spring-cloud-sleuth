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

import org.springframework.context.support.StaticApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.BDDAssertions.then;

class PlatformTransactionManagerInstrumenterTests {

	@Test
	void should_return_true_when_instruments_platform_transaction_manager() {
		PlatformTransactionManagerInstrumenter instrumenter = new PlatformTransactionManagerInstrumenter(
				new StaticApplicationContext());

		then(instrumenter.isApplicableForInstrumentation(platformTransactionManager())).isTrue();
	}

	@Test
	void should_return_false_when_instruments_abstract_platform_transaction_manager() {
		PlatformTransactionManagerInstrumenter instrumenter = new PlatformTransactionManagerInstrumenter(
				new StaticApplicationContext());

		then(instrumenter.isApplicableForInstrumentation(abstractPlatformTransactionManager())).isFalse();
	}

	@Test
	void should_return_false_when_instruments_non_platform_transaction_manager() {
		PlatformTransactionManagerInstrumenter instrumenter = new PlatformTransactionManagerInstrumenter(
				new StaticApplicationContext());

		then(instrumenter.isApplicableForInstrumentation("not platform transaction manager")).isFalse();
	}

	private PlatformTransactionManager platformTransactionManager() {
		return new PlatformTransactionManager() {
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
		};
	}

	private PlatformTransactionManager abstractPlatformTransactionManager() {
		return new AbstractPlatformTransactionManager() {
			@Override
			protected Object doGetTransaction() throws TransactionException {
				return null;
			}

			@Override
			protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {

			}

			@Override
			protected void doCommit(DefaultTransactionStatus status) throws TransactionException {

			}

			@Override
			protected void doRollback(DefaultTransactionStatus status) throws TransactionException {

			}
		};
	}

}
