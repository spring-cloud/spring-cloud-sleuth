/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.async;

import java.lang.reflect.Method;

import brave.Span;
import brave.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.cloud.sleuth.SpanNamer;
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

	private static final String CLASS_KEY = "class";
	private static final String METHOD_KEY = "method";

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
		span = span.name(spanName);
		try(Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			span.tag(CLASS_KEY, pjp.getTarget().getClass().getSimpleName());
			span.tag(METHOD_KEY, pjp.getSignature().getName());
			return pjp.proceed();
		} finally {
			span.finish();
		}
	}

	String name(ProceedingJoinPoint pjp) {
		return this.spanNamer.name(getMethod(pjp, pjp.getTarget()),
				SpanNameUtil.toLowerHyphen(pjp.getSignature().getName()));
	}

	private Method getMethod(ProceedingJoinPoint pjp, Object object) {
		MethodSignature signature = (MethodSignature) pjp.getSignature();
		Method method = signature.getMethod();
		return ReflectionUtils
				.findMethod(object.getClass(), method.getName(), method.getParameterTypes());
	}

}
