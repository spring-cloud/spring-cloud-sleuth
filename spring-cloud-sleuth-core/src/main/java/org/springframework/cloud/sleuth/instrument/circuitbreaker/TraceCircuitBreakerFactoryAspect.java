/*
 * Copyright 2018-2019 the original author or authors.
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
import org.aspectj.lang.annotation.Pointcut;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.sleuth.api.Tracer;

@Aspect
class TraceCircuitBreakerFactoryAspect {

	private final Tracer tracer;

	TraceCircuitBreakerFactoryAspect(Tracer tracer) {
		this.tracer = tracer;
	}

	@Pointcut("execution(public * org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory.create(..))")
	private void anyCircuitBreakerFactoryCreate() {
	} // NOSONAR

	@Around("anyCircuitBreakerFactoryCreate()")
	public Object wrapFactory(ProceedingJoinPoint pjp) throws Throwable {
		CircuitBreaker circuitBreaker = (CircuitBreaker) pjp.proceed();
		return new TraceCircuitBreaker(circuitBreaker, this.tracer);
	}

}
