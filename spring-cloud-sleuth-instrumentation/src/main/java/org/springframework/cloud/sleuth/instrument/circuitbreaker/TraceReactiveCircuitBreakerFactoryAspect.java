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

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Tracer;

@Aspect
public class TraceReactiveCircuitBreakerFactoryAspect {

	private final Tracer tracer;

	private final CurrentTraceContext currentTraceContext;

	public TraceReactiveCircuitBreakerFactoryAspect(Tracer tracer, CurrentTraceContext currentTraceContext) {
		this.tracer = tracer;
		this.currentTraceContext = currentTraceContext;
	}

	@Around("execution(public * org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory.create(..))")
	public Object wrapFactory(ProceedingJoinPoint pjp) throws Throwable {
		ReactiveCircuitBreaker circuitBreaker = (ReactiveCircuitBreaker) pjp.proceed();
		return new TraceReactiveCircuitBreaker(circuitBreaker, this.tracer, this.currentTraceContext);
	}

}
