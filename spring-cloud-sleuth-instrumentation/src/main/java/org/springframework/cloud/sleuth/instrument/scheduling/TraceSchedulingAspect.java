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

package org.springframework.cloud.sleuth.instrument.scheduling;

import java.util.regex.Pattern;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
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
public class TraceSchedulingAspect {

	private final Tracer tracer;

	@Nullable
	private final Pattern skipPattern;

	public TraceSchedulingAspect(Tracer tracer, Pattern skipPattern) {
		this.tracer = tracer;
		this.skipPattern = skipPattern;
	}

	@Around("execution (@org.springframework.scheduling.annotation.Scheduled  * *.*(..))")
	public Object traceBackgroundThread(final ProceedingJoinPoint pjp) throws Throwable {
		if (this.skipPattern != null && this.skipPattern.matcher(pjp.getTarget().getClass().getName()).matches()) {
			// we might have a span in context due to wrapping of runnables
			// we want to clear that context
			this.tracer.withSpan(null);
			return pjp.proceed();
		}
		String spanName = SpanNameUtil.toLowerHyphen(pjp.getSignature().getName());
		AssertingSpan span = SleuthSchedulingSpan.SCHEDULED_ANNOTATION_SPAN.wrap(startOrContinueSpan()).name(spanName);
		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			span.tag(SleuthSchedulingSpan.Tags.CLASS, pjp.getTarget().getClass().getSimpleName())
					.tag(SleuthSchedulingSpan.Tags.METHOD, pjp.getSignature().getName());
			return pjp.proceed();
		}
		catch (Throwable ex) {
			span.error(ex);
			throw ex;
		}
		finally {
			span.end();
		}
	}

	private Span startOrContinueSpan() {
		Span currentSpan = this.tracer.currentSpan();
		if (currentSpan != null) {
			return currentSpan;
		}
		return SleuthSchedulingSpan.SCHEDULED_ANNOTATION_SPAN.wrap(this.tracer.nextSpan());
	}

}
