/*
 * Copyright 2013-2018 the original author or authors.
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

import java.lang.reflect.Method;

import brave.Span;
import brave.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.util.SpanNameUtil;
import org.springframework.util.ReflectionUtils;

/**
 * Aspect that creates a new Span for running threads executing methods annotated with
 * {@link org.springframework.scheduling.annotation.Async} annotation.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 *
 * @see Tracer
 */
@Aspect
public class TraceAsyncAspect {

	private final Tracer tracer;
	private final SpanNamer spanNamer;
	private final TraceKeys traceKeys;

	public TraceAsyncAspect(Tracer tracer, SpanNamer spanNamer, TraceKeys traceKeys) {
		this.tracer = tracer;
		this.spanNamer = spanNamer;
		this.traceKeys = traceKeys;
	}

	@Around("execution (@org.springframework.scheduling.annotation.Async  * *.*(..))")
	public Object traceBackgroundThread(final ProceedingJoinPoint pjp) throws Throwable {
		String spanName = this.spanNamer.name(getMethod(pjp, pjp.getTarget()),
				SpanNameUtil.toLowerHyphen(pjp.getSignature().getName()));
		Span span = this.tracer.currentSpan().name(spanName);
		try(Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			span.tag(this.traceKeys.getAsync().getPrefix() +
					this.traceKeys.getAsync().getClassNameKey(), pjp.getTarget().getClass().getSimpleName());
			span.tag(this.traceKeys.getAsync().getPrefix() +
					this.traceKeys.getAsync().getMethodNameKey(), pjp.getSignature().getName());
			return pjp.proceed();
		} finally {
			span.finish();
		}
	}

	private Method getMethod(ProceedingJoinPoint pjp, Object object) {
		MethodSignature signature = (MethodSignature) pjp.getSignature();
		Method method = signature.getMethod();
		return ReflectionUtils
				.findMethod(object.getClass(), method.getName(), method.getParameterTypes());
	}

}
