package org.springframework.cloud.sleuth.instrument.scheduling;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Aspect that creates a new Span for running threads executing methods annotated with {@link Scheduled} annotation.
 * For every execution of scheduled method a new trace will be started.
 *
 * @author Tomasz Nurkewicz, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Spencer Gibb
 *
 * @see Trace
 */
@Aspect
public class TraceSchedulingAspect {

	private final Trace trace;

	public TraceSchedulingAspect(Trace trace) {
		this.trace = trace;
	}

	@Around("execution (@org.springframework.scheduling.annotation.Scheduled  * *.*(..))")
	public Object traceSceduledThread(final ProceedingJoinPoint pjp) throws Throwable {
		TraceScope scope = trace.startSpan(Span.Type.CLIENT, pjp.toShortString());
		try {
			return pjp.proceed();
		} finally {
			scope.close();
		}
	}
}
