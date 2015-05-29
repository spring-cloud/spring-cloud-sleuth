/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.sleuth.correlation.scheduling;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.sleuth.correlation.CorrelationIdUpdater;
import org.springframework.cloud.sleuth.correlation.UuidGenerator;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Callable;

/**
 * Aspect that sets correlationId for running threads executing methods annotated with {@link Scheduled} annotation.
 * For every execution of scheduled method a new, i.e. unique one, value of correlationId will be set.
 *
 * @author Tomasz Nurkewicz, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 *
 * @see UuidGenerator
 * @see CorrelationIdUpdater
 */
@Aspect
public class ScheduledTaskWithCorrelationIdAspect {

	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final UuidGenerator uuidGenerator;

	public ScheduledTaskWithCorrelationIdAspect(UuidGenerator uuidGenerator) {
		this.uuidGenerator = uuidGenerator;
	}

	@Around("execution (@org.springframework.scheduling.annotation.Scheduled  * *.*(..))")
	public Object setNewCorrelationIdOnThread(final ProceedingJoinPoint pjp) throws Throwable {
		String correlationId = uuidGenerator.create();
		return CorrelationIdUpdater.withId(correlationId, new Callable() {
			@Override
			public Object call() throws Exception {
				try {
					return pjp.proceed();
				} catch (Throwable throwable) {
					log.error("Didn't manage to proceed with the pointcut", throwable);
					throw new RuntimeException(throwable);
				}
			}
		});
	}
}
