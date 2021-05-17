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

package org.springframework.cloud.sleuth.instrument.async;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.cloud.sleuth.internal.SpanNameUtil;
import org.springframework.util.ReflectionUtils;

/**
 * Aspect that creates a new Span for running threads executing methods annotated with
 * {@link org.springframework.scheduling.annotation.Async} annotation.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 * @see Tracer
 */
@Aspect
public class TraceAsyncAspect {

	private final Tracer tracer;

	private final SpanNamer spanNamer;

	public TraceAsyncAspect(Tracer tracer, SpanNamer spanNamer) {
		this.tracer = tracer;
		this.spanNamer = spanNamer;
	}

	@Around("execution (@org.springframework.scheduling.annotation.Async  * *.*(..))")
	public Object traceBackgroundThread(final ProceedingJoinPoint pjp) throws Throwable {
		String spanName = name(pjp);
		Span span = this.tracer.currentSpan();
		if (span == null) {
			span = this.tracer.nextSpan();
		}
		AssertingSpan assertingSpan = SleuthAsyncSpan.ASYNC_ANNOTATION_SPAN.wrap(span).name(spanName);
		try (Tracer.SpanInScope ws = this.tracer.withSpan(assertingSpan.start())) {
			assertingSpan.tag(SleuthAsyncSpan.Tags.CLASS, pjp.getTarget().getClass().getSimpleName())
					.tag(SleuthAsyncSpan.Tags.METHOD, pjp.getSignature().getName());
			return pjp.proceed();
		}
		finally {
			assertingSpan.end();
		}
	}

	String name(ProceedingJoinPoint pjp) {
		return this.spanNamer.name(getMethod(pjp, pjp.getTarget()),
				SpanNameUtil.toLowerHyphen(pjp.getSignature().getName()));
	}

	private Method getMethod(ProceedingJoinPoint pjp, Object object) {
		MethodSignature signature = (MethodSignature) pjp.getSignature();
		Method method = signature.getMethod();
		return ReflectionUtils.findMethod(object.getClass(), method.getName(), method.getParameterTypes());
	}

}
