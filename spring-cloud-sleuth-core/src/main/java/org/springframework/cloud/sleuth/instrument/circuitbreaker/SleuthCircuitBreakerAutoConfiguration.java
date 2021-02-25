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

package org.springframework.cloud.sleuth.instrument.circuitbreaker;

import java.util.function.Function;
import java.util.function.Supplier;

import brave.Tracer;
import brave.Tracing;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that registers instrumentation for circuit breakers.
 *
 * @author Marcin Grzejszczak
 * @since 2.2.1
 * @deprecated This type should have never been public and will be hidden or removed in
 * 3.0
 */
@Deprecated
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@ConditionalOnClass(CircuitBreaker.class)
@ConditionalOnBean(Tracing.class)
@ConditionalOnProperty(value = "spring.sleuth.circuitbreaker.enabled",
		matchIfMissing = true)
@EnableConfigurationProperties(SleuthCircuitBreakerProperties.class)
public class SleuthCircuitBreakerAutoConfiguration {

	@Bean
	TraceCircuitBreakerFactoryAspect traceCircuitBreakerFactoryAspect(Tracer tracer) {
		return new TraceCircuitBreakerFactoryAspect(tracer);
	}

}

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

class TraceCircuitBreaker implements CircuitBreaker {

	private final CircuitBreaker delegate;

	private final Tracer tracer;

	TraceCircuitBreaker(CircuitBreaker delegate, Tracer tracer) {
		this.delegate = delegate;
		this.tracer = tracer;
	}

	@Override
	public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
		return this.delegate.run(new TraceSupplier<>(this.tracer, toRun),
				new TraceFunction<>(this.tracer, fallback));
	}

	@Override
	public <T> T run(Supplier<T> toRun) {
		return this.delegate.run(new TraceSupplier<>(this.tracer, toRun));
	}

}
