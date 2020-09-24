/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.scheduling;

import java.util.regex.Pattern;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import org.springframework.cloud.sleuth.internal.SpanNameUtil;
import org.springframework.lang.Nullable;

/**
 * Aspect that creates a new Span for running threads executing methods annotated with
 * {@link org.springframework.scheduling.annotation.Scheduled} annotation. For every
 * execution of scheduled method a new trace will be started. The name of the span will be
 * the simple name of the class annotated with
 * {@link org.springframework.scheduling.annotation.Scheduled}
 *
 * @author Tomasz Nurkewicz, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @since 1.0.0
 */
@Aspect
class TraceSchedulingAspect {

	private static final String CLASS_KEY = "class";

	private static final String METHOD_KEY = "method";

	private final Tracer tracer;

	@Nullable
	private final Pattern skipPattern;

	TraceSchedulingAspect(Tracer tracer, Pattern skipPattern) {
		this.tracer = tracer;
		this.skipPattern = skipPattern;
	}

	@Around("execution (@org.springframework.scheduling.annotation.Scheduled  * *.*(..))")
	public Object traceBackgroundThread(final ProceedingJoinPoint pjp) throws Throwable {
		if (this.skipPattern != null && this.skipPattern.matcher(pjp.getTarget().getClass().getName()).matches()) {
			return pjp.proceed();
		}
		String spanName = SpanNameUtil.toLowerHyphen(pjp.getSignature().getName());
		Span span = startOrContinueRenamedSpan(spanName);
		try (Scope scope = this.tracer.withSpan(span)) {
			span.setAttribute(CLASS_KEY, pjp.getTarget().getClass().getSimpleName());
			span.setAttribute(METHOD_KEY, pjp.getSignature().getName());
			return pjp.proceed();
		}
		catch (Throwable ex) {
			String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			span.setAttribute("error", message);
			throw ex;
		}
		finally {
			span.end();
		}
	}

	private Span startOrContinueRenamedSpan(String spanName) {
		Span currentSpan = this.tracer.getCurrentSpan();
		if (currentSpan != null) {
			currentSpan.updateName(spanName);
			return currentSpan;
		}
		return this.tracer.spanBuilder(spanName).startSpan();
	}

}
