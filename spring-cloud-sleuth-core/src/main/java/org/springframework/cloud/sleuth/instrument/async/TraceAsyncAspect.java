/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.async;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cloud.sleuth.SpanHolder;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Aspect that creates a new Span for running threads executing methods annotated with
 * {@link org.springframework.scheduling.annotation.Async} annotation.
 *
 * @author Marcin Grzejszczak
 *
 * @see Tracer
 */
@Aspect
public class TraceAsyncAspect {

	private static final String ASYNC_COMPONENT = "async";

	private final Tracer tracer;

	public TraceAsyncAspect(Tracer tracer) {
		this.tracer = tracer;
	}

	@Around("execution (@org.springframework.scheduling.annotation.Async  * *.*(..))")
	public Object traceBackgroundThread(final ProceedingJoinPoint pjp) throws Throwable {
		SpanName spanName = new SpanName(ASYNC_COMPONENT,
				pjp.getTarget().getClass().getSimpleName(),
				"method=" + pjp.getSignature().getName());
		SpanHolder span = SpanHolder.span(this.tracer).startOrContinueSpan(spanName);
		try {
			return pjp.proceed();
		} finally {
			// not detaching since the same thread is used by the lazyexecutor
			span.closeIfCreated();
		}
	}

}
