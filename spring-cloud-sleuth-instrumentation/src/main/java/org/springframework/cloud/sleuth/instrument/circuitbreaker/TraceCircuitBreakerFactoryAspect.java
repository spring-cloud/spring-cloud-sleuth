/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.circuitbreaker;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Aspec around {@link CircuitBreaker} creation.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Aspect
public class TraceCircuitBreakerFactoryAspect {

	private final Tracer tracer;

	public TraceCircuitBreakerFactoryAspect(Tracer tracer) {
		this.tracer = tracer;
	}

	@Around("execution(public * org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory.create(..))")
	public Object wrapFactory(ProceedingJoinPoint pjp) throws Throwable {
		CircuitBreaker circuitBreaker = (CircuitBreaker) pjp.proceed();
		return new TraceCircuitBreaker(circuitBreaker, this.tracer);
	}

}
