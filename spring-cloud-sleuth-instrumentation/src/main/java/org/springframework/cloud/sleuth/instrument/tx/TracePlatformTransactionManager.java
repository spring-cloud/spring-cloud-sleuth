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

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAndScope;
import org.springframework.cloud.sleuth.ThreadLocalSpan;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

/**
 * A trace representation of a {@link PlatformTransactionManager}.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TracePlatformTransactionManager implements PlatformTransactionManager {

	private static final Log log = LogFactory.getLog(TracePlatformTransactionManager.class);

	protected final PlatformTransactionManager delegate;

	private final BeanFactory beanFactory;

	private Tracer tracer;

	volatile ThreadLocalSpan threadLocalSpan;

	public TracePlatformTransactionManager(PlatformTransactionManager delegate, BeanFactory beanFactory) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
	}

	@PostConstruct
	void initialize() {
		if (this.threadLocalSpan == null) {
			this.threadLocalSpan = new ThreadLocalSpan(tracer());
		}
	}

	@Override
	public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
		initialize();
		SpanAndScope spanAndScope = this.threadLocalSpan.get();
		Span currentSpan = spanAndScope != null ? spanAndScope.getSpan() : tracer().currentSpan();
		Span span = fallbackSpan();
		try {
			TransactionDefinition def = (definition != null ? definition : TransactionDefinition.withDefaults());
			TransactionStatus status = this.delegate.getTransaction(definition);
			taggedSpan(currentSpan, span, def, status);
			return status;
		}
		catch (Exception e) {
			if (log.isDebugEnabled()) {
				log.debug(
						"Exception occurred while trying to get a transaction, will mark the span with error and report it");
			}
			span.error(e);
			span.end();
			throw e;
		}
	}

	Span fallbackSpan() {
		return SleuthTxSpan.TX_SPAN.wrap(tracer().nextSpan()).name(SleuthTxSpan.TX_SPAN.getName()).start();
	}

	private Span taggedSpan(Span currentSpan, Span span, TransactionDefinition def, TransactionStatus status) {
		Span taggedSpan = span;
		if (status.isNewTransaction() || currentSpan == null) {
			if (log.isDebugEnabled()) {
				log.debug("Creating new span [" + taggedSpan + "] cause a new transaction is started");
			}
			TracePlatformTransactionManagerTags.tag(taggedSpan, def, this.delegate.getClass());
		}
		else {
			taggedSpan = currentSpan;
		}
		this.threadLocalSpan.set(taggedSpan);
		return taggedSpan;
	}

	@Override
	public void commit(TransactionStatus status) throws TransactionException {
		SpanAndScope spanAndScope = this.threadLocalSpan.get();
		if (spanAndScope == null) {
			if (log.isDebugEnabled()) {
				log.debug("No span and scope found - this shouldn't happen, sth is wrong");
			}
			this.delegate.commit(status);
			return;
		}
		Exception ex = null;
		Span span = spanAndScope.getSpan();
		try {
			if (log.isDebugEnabled()) {
				log.debug("Wrapping commit");
			}
			this.delegate.commit(status);
		}
		catch (Exception e) {
			ex = e;
			span.error(e);
			throw e;
		}
		finally {
			SleuthTxSpan.TX_SPAN.wrap(span).event(SleuthTxSpan.Events.COMMIT);
			spanAndScope.close();
			if (ex == null) {
				if (log.isDebugEnabled()) {
					log.debug("No exception was found - will clear thread local span");
				}
				this.threadLocalSpan.remove();
			}
			if (log.isDebugEnabled()) {
				log.debug("Restored thread local span [" + this.threadLocalSpan.get() + "]");
			}
		}
	}

	@Override
	public void rollback(TransactionStatus status) throws TransactionException {
		SpanAndScope spanAndScope = this.threadLocalSpan.get();
		if (spanAndScope == null) {
			if (log.isDebugEnabled()) {
				log.debug("No span and scope found - this shouldn't happen, sth is wrong");
			}
			this.delegate.rollback(status);
			return;
		}
		Span span = spanAndScope.getSpan();
		try {
			if (log.isDebugEnabled()) {
				log.debug("Wrapping rollback");
			}
			this.delegate.rollback(status);
		}
		catch (Exception e) {
			span.error(e);
			throw e;
		}
		finally {
			SleuthTxSpan.TX_SPAN.wrap(span).event(SleuthTxSpan.Events.ROLLBACK);
			spanAndScope.close();
			this.threadLocalSpan.remove();
			if (log.isDebugEnabled()) {
				log.debug("Restored thread local span [" + this.threadLocalSpan.get() + "]");
			}
		}
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

}
