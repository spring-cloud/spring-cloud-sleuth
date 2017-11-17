/*
 * Copyright 2013-2017 the original author or authors.
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

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.InternalApi;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
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

	private static final String ASYNC_COMPONENT = "async";

	private final Tracer tracer;
	private final TraceKeys traceKeys;
	private final BeanFactory beanFactory;
	private SpanNamer spanNamer;

	@Deprecated
	public TraceAsyncAspect(Tracer tracer, TraceKeys traceKeys, BeanFactory beanFactory) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.beanFactory = beanFactory;
	}

	public TraceAsyncAspect(Tracer tracer, TraceKeys traceKeys, SpanNamer spanNamer) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.spanNamer = spanNamer;
		this.beanFactory = null;
	}

	@Around("execution (@org.springframework.scheduling.annotation.Async  * *.*(..))")
	public Object traceBackgroundThread(final ProceedingJoinPoint pjp) throws Throwable {
		String spanName = spanNamer().name(getMethod(pjp, pjp.getTarget()),
				SpanNameUtil.toLowerHyphen(pjp.getSignature().getName()));
		Span span = span(spanName);
		renameAsyncSpan(spanName, span);
		this.tracer.addTag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, ASYNC_COMPONENT);
		this.tracer.addTag(this.traceKeys.getAsync().getPrefix() +
				this.traceKeys.getAsync().getClassNameKey(), pjp.getTarget().getClass().getSimpleName());
		this.tracer.addTag(this.traceKeys.getAsync().getPrefix() +
				this.traceKeys.getAsync().getMethodNameKey(), pjp.getSignature().getName());
		try {
			return pjp.proceed();
		} finally {
			this.tracer.close(span);
		}
	}

	private void renameAsyncSpan(String spanName, Span span) {
		// if there's a tag "lc" -> "async", that means the span came from
		// a LazyTraceExecutor component that creates a span that contains very few
		// information. If that's the case we want to rename it to have a different name
		if (ASYNC_COMPONENT.equals(span.tags().get(Span.SPAN_LOCAL_COMPONENT_TAG_NAME))) {
			InternalApi.renameSpan(span, spanName);
		}
	}

	private Span span(String spanName) {
		if (this.tracer.isTracing()) {
			return this.tracer.getCurrentSpan();
		}
		return this.tracer.createSpan(spanName);
	}

	private Method getMethod(ProceedingJoinPoint pjp, Object object) {
		MethodSignature signature = (MethodSignature) pjp.getSignature();
		Method method = signature.getMethod();
		return ReflectionUtils
				.findMethod(object.getClass(), method.getName(), method.getParameterTypes());
	}

	SpanNamer spanNamer() {
		if (this.spanNamer == null && this.beanFactory != null) {
			this.spanNamer = this.beanFactory.getBean(SpanNamer.class);
		}
		return this.spanNamer;
	}

}
