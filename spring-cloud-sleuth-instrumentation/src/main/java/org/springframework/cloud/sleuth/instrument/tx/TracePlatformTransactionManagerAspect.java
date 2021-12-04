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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jetbrains.annotations.NotNull;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.ThreadLocalSpan;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * An aspect around {@link PlatformTransactionManager}.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.1
 */
@Aspect
public class TracePlatformTransactionManagerAspect {

	private final BeanFactory beanFactory;

	volatile ThreadLocalSpan threadLocalSpan;

	private static final Map<PlatformTransactionManager, TracePlatformTransactionManager> CACHE = new ConcurrentHashMap<>();

	public TracePlatformTransactionManagerAspect(Tracer tracer, BeanFactory beanFactory) {
		this.threadLocalSpan = new ThreadLocalSpan(tracer);
		this.beanFactory = beanFactory;
	}

	@Around(value = "execution (* org.springframework.transaction.PlatformTransactionManager.commit(..)) && this(manager)",
			argNames = "pjp,manager")
	public Object traceCommit(final ProceedingJoinPoint pjp, PlatformTransactionManager manager) {
		TransactionStatus transactionStatus = (TransactionStatus) pjp.getArgs()[0];
		tracedManager(manager).commit(transactionStatus);
		return null;
	}

	@Around(value = "execution (* org.springframework.transaction.PlatformTransactionManager.rollback(..)) && this(manager)",
			argNames = "pjp,manager")
	public Object traceRollback(final ProceedingJoinPoint pjp, PlatformTransactionManager manager) {
		TransactionStatus transactionStatus = (TransactionStatus) pjp.getArgs()[0];
		tracedManager(manager).rollback(transactionStatus);
		return null;
	}

	@Around(value = "execution (* org.springframework.transaction.PlatformTransactionManager.getTransaction(..)) && this(manager)",
			argNames = "pjp,manager")
	public Object traceGetTransaction(final ProceedingJoinPoint pjp, PlatformTransactionManager manager) {
		TransactionDefinition transactionDefinition = (TransactionDefinition) pjp.getArgs()[0];
		return tracedManager(manager).getTransaction(transactionDefinition);
	}

	@NotNull
	private TracePlatformTransactionManager tracedManager(PlatformTransactionManager manager) {
		return CACHE.computeIfAbsent(manager,
				platformTransactionManager -> new TracePlatformTransactionManager(platformTransactionManager,
						this.beanFactory));
	}

}
