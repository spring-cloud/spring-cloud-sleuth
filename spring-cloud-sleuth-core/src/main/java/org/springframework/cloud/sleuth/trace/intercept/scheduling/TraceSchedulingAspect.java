package org.springframework.cloud.sleuth.trace.intercept.scheduling;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cloud.sleuth.trace.Trace;
import org.springframework.cloud.sleuth.trace.TraceScope;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Aspect that sets correlationId for running threads executing methods annotated with {@link Scheduled} annotation.
 * For every execution of scheduled method a new, i.e. unique one, value of correlationId will be set.
 *
 * @author Tomasz Nurkewicz, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Spencer Gibb
 *
 * @see org.springframework.cloud.sleuth.trace.Trace
 */
@Aspect
public class TraceSchedulingAspect {

	private final Trace trace;

	public TraceSchedulingAspect(Trace trace) {
		this.trace = trace;
	}

	@Around("execution (@org.springframework.scheduling.annotation.Scheduled  * *.*(..))")
	public Object setNewCorrelationIdOnThread(final ProceedingJoinPoint pjp) throws Throwable {
		TraceScope scope = trace.startSpan(pjp.toShortString());
		try {
			return pjp.proceed();
		} finally {
			scope.close();
		}
	}
}
