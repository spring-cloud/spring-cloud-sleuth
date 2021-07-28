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

package org.springframework.cloud.sleuth.instrument.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;

/**
 * Aspect wrapping resolution of properties.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
@Aspect
public class TraceEnvironmentRepositoryAspect {

	private final Tracer tracer;

	public TraceEnvironmentRepositoryAspect(Tracer tracer) {
		this.tracer = tracer;
	}

	@Around("execution (* org.springframework.cloud.config.server.environment.EnvironmentRepository.*(..))")
	public Object traceFindEnvironment(final ProceedingJoinPoint pjp) throws Throwable {
		// @formatter:off
		AssertingSpan findOneSpan = SleuthConfigSpan.CONFIG_SPAN.wrap(this.tracer.nextSpan())
			.name(SleuthConfigSpan.CONFIG_SPAN.getName())
			.tag(SleuthConfigSpan.Tags.ENVIRONMENT_CLASS, pjp.getTarget().getClass().getName())
			.tag(SleuthConfigSpan.Tags.ENVIRONMENT_METHOD, pjp.getSignature().getName());
		// @formatter:on
		try (Tracer.SpanInScope ws = this.tracer.withSpan(findOneSpan.start())) {
			return pjp.proceed();
		}
		finally {
			findOneSpan.end();
		}
	}

}
