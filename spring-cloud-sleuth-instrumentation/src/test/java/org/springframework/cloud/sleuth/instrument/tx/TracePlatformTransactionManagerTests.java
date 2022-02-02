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

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAndScope;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.cloud.sleuth.tracer.SimpleSpan;
import org.springframework.cloud.sleuth.tracer.SimpleTracer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.BDDAssertions.then;

class TracePlatformTransactionManagerTests {

	SimpleTracer tracer = new SimpleTracer();

	PlatformTransactionManager delegate = BDDMockito.mock(PlatformTransactionManager.class);

	@Test
	void should_create_a_new_span_for_a_new_committed_transaction() {
		// given
		TracePlatformTransactionManager manager = manager();
		setupTransactionStatusWithNewTransactionStatusEqualTo(true);
		thenThreadLocalIsClear(manager);

		// when
		TransactionStatus transaction = manager.getTransaction(null);
		// then
		SimpleSpan span = thenATaggedSpanWasCreated(manager);

		// when
		manager.commit(transaction);
		// then
		thenOneSpanWasReported(manager, span);
		thenThreadLocalIsClear(manager);
		then(tracer.currentSpan()).isNull();
	}

	@Test
	void should_create_a_new_span_for_a_new_rolled_back_transaction() {
		// given
		TracePlatformTransactionManager manager = manager();
		setupTransactionStatusWithNewTransactionStatusEqualTo(true);
		thenThreadLocalIsClear(manager);

		// when
		TransactionStatus transaction = manager.getTransaction(null);
		// then
		SimpleSpan span = thenATaggedSpanWasCreated(manager);

		// when
		manager.rollback(transaction);
		// then
		thenOneSpanWasReported(manager, span);
		thenThreadLocalIsClear(manager);
		then(tracer.currentSpan()).isNull();
	}

	@Test
	void should_continue_a_span_for_a_the_same_transaction() {
		// given
		TracePlatformTransactionManager manager = managerWithManualFallback();
		setupTransactionStatusWithNewTransactionStatusEqualTo(false);
		SimpleSpan firstSpan = threadLocalSpan(manager);

		// when
		TransactionStatus transaction = manager.getTransaction(null);
		// then
		SpanAndScope spanAndScope = manager.threadLocalSpan.get();
		then(spanAndScope).isNotNull();
		then(spanAndScope.getSpan()).isSameAs(firstSpan);

		// when
		manager.commit(transaction);
		// then
		thenPreviouslyCreatedSpanWasFinished(firstSpan);
		then(firstSpan).isSameAs(manager.threadLocalSpan.get().getSpan());
		manager.threadLocalSpan.remove();
		thenThreadLocalIsClear(manager);
		then(tracer.currentSpan()).isNull();
	}

	private SimpleSpan threadLocalSpan(TracePlatformTransactionManager manager) {
		SimpleSpan firstSpan = tracer.nextSpan().start();
		manager.threadLocalSpan.set(firstSpan);
		return firstSpan;
	}

	@Test
	void should_report_a_fallback_span_when_exception_occurred_while_getting_transaction() {
		TracePlatformTransactionManager manager = manager();
		BDDMockito.given(getDelegate().getTransaction(BDDMockito.any()))
				.willThrow(new TransactionTimedOutException("boom"));
		thenThreadLocalIsClear(manager);

		BDDAssertions.thenThrownBy(() -> manager.getTransaction(null)).isInstanceOf(TransactionTimedOutException.class);

		SimpleSpan span = tracer.getOnlySpan();
		then(span.throwable).isInstanceOf(TransactionTimedOutException.class);
	}

	@Test
	void should_report_a_span_when_exception_occurred_while_committing_transaction() {
		TracePlatformTransactionManager manager = manager();
		BDDMockito.willThrow(new TransactionTimedOutException("boom")).given(getDelegate()).commit(BDDMockito.any());
		threadLocalSpan(manager);

		BDDAssertions.thenThrownBy(() -> manager.commit(null)).isInstanceOf(TransactionTimedOutException.class);

		SimpleSpan span = tracer.getOnlySpan();
		then(span.throwable).isInstanceOf(TransactionTimedOutException.class);
		manager.threadLocalSpan.remove();
		thenThreadLocalIsClear(manager);
		then(tracer.currentSpan()).isNull();
	}

	@Test
	void should_report_a_span_when_exception_occurred_while_rolling_back_transaction() {
		TracePlatformTransactionManager manager = manager();
		BDDMockito.willThrow(new TransactionTimedOutException("boom")).given(getDelegate()).rollback(BDDMockito.any());
		threadLocalSpan(manager);

		BDDAssertions.thenThrownBy(() -> manager.rollback(null)).isInstanceOf(TransactionTimedOutException.class);

		SimpleSpan span = tracer.getOnlySpan();
		then(span.throwable).isInstanceOf(TransactionTimedOutException.class);
		manager.threadLocalSpan.remove();
		thenThreadLocalIsClear(manager);
		then(tracer.currentSpan()).isNull();
	}

	private void setupTransactionStatusWithNewTransactionStatusEqualTo(boolean transactionStatus) {
		BDDMockito.given(getDelegate().getTransaction(BDDMockito.any()))
				.willReturn(new SimpleTransactionStatus(transactionStatus));
	}

	private void thenThreadLocalIsClear(TracePlatformTransactionManager manager) {
		then(manager.threadLocalSpan.get()).as("Thread local was cleared").isNull();
	}

	private void thenOneSpanWasReported(TracePlatformTransactionManager manager, SimpleSpan firstSpan) {
		thenPreviouslyCreatedSpanWasFinished(firstSpan);
		thenThreadLocalIsClear(manager);
	}

	private void thenPreviouslyCreatedSpanWasFinished(SimpleSpan firstSpan) {
		then(firstSpan.ended).as("The previously created span was finished").isTrue();
		then(tracer.getOnlySpan()).as("The previously created span was reported").isSameAs(firstSpan);
	}

	TracePlatformTransactionManager manager() {
		final TracePlatformTransactionManager manager = new TracePlatformTransactionManager(getDelegate(),
				beanFactory());
		manager.initialize();
		return manager;
	}

	private TracePlatformTransactionManager managerWithManualFallback() {
		final TracePlatformTransactionManager manager = new TracePlatformTransactionManager(getDelegate(),
				beanFactory()) {
			@Override
			Span fallbackSpan() {
				return new SimpleSpan().start();
			}
		};
		manager.initialize();
		return manager;
	}

	private SimpleSpan thenATaggedSpanWasCreated(TracePlatformTransactionManager manager) {
		SpanAndScope spanAndScope = manager.threadLocalSpan.get();
		then(spanAndScope).isNotNull();
		SimpleSpan span = AssertingSpan.unwrap(spanAndScope.getSpan());
		then(span.started).isTrue();
		then(span.tags).isNotEmpty();
		return span;
	}

	BeanFactory beanFactory() {
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
		beanFactory.addBean("tracer", tracer);
		return beanFactory;
	}

	PlatformTransactionManager getDelegate() {
		return this.delegate;
	}

}
