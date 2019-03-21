/*
 * Copyright 2013-2017 the original author or authors.
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

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.util.SpanNameUtil;

/**
 * Aspect that creates a new Span for running threads executing methods annotated with
 * {@link org.springframework.scheduling.annotation.Scheduled} annotation.
 * For every execution of scheduled method a new trace will be started. The name of the
 * span will be the simple name of the class annotated with
 * {@link org.springframework.scheduling.annotation.Scheduled}
 *
 * @author Tomasz Nurkewicz, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @since 1.0.0
 *
 * @see Tracer
 */
@Aspect
public class TraceSchedulingAspect {

	private static final String SCHEDULED_COMPONENT = "scheduled";

	private final Tracer tracer;
	private final TraceKeys traceKeys;
	private final Pattern skipPattern;

	public TraceSchedulingAspect(Tracer tracer, TraceKeys traceKeys, Pattern skipPattern) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.skipPattern = skipPattern;
	}

	@Around("execution (@org.springframework.scheduling.annotation.Scheduled  * *.*(..))")
	public Object traceBackgroundThread(final ProceedingJoinPoint pjp) throws Throwable {
		if (this.skipPattern.matcher(pjp.getTarget().getClass().getName()).matches()) {
			return pjp.proceed();
		}
		String spanName = SpanNameUtil.toLowerHyphen(pjp.getSignature().getName());
		Span span = this.tracer.createSpan(spanName);
		this.tracer.addTag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, SCHEDULED_COMPONENT);
		this.tracer.addTag(this.traceKeys.getAsync().getPrefix() +
				this.traceKeys.getAsync().getClassNameKey(), pjp.getTarget().getClass().getSimpleName());
		this.tracer.addTag(this.traceKeys.getAsync().getPrefix() +
				this.traceKeys.getAsync().getMethodNameKey(), pjp.getSignature().getName());
		try {
			return pjp.proceed();
		}
		finally {
			// #842 - the span might have already been closed
			if (this.tracer.isTracing()) {
				this.tracer.close(span);
			}
		}
	}

}
