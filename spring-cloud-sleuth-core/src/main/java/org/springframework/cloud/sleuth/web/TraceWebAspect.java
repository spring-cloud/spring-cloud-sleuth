package org.springframework.cloud.sleuth.web;

import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.apachecommons.CommonsLog;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestOperations;

/**
 * Aspect that adds correlation id to
 * <p/>
 * <ul>
 * <li>{@link RestController} annotated classes</li>
 * <li>{@link Controller} annotated classes</li>
 * <li>explicit {@link RestOperations}.exchange(..) method calls</li>
 * </ul>
 * <p/>
 * For controllers an around aspect is created that create a {@link Span} for each call.
 * <p/>
 * For {@link RestOperations} we are wrapping all executions of the <b>exchange</b>
 * methods and we are extracting {@link HttpHeaders} from the passed {@link HttpEntity}.
 * Next we are adding span id header * {@link Trace#SPAN_ID_NAME} with the value taken
 * from the current Span. Finally the method execution proceeds.
 *
 * @see RestController
 * @see Controller
 * @see RestOperations
 * @see Trace
 *
 * @author Tomasz Nurkewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 * @author Spencer Gibb
 */
@CommonsLog
public class TraceWebAspect {

	private final Trace trace;

	public TraceWebAspect(Trace trace) {
		this.trace = trace;
	}

	@Pointcut("@target(org.springframework.web.bind.annotation.RestController)")
	private void anyRestControllerAnnotated() {
	}

	@Pointcut("@target(org.springframework.stereotype.Controller)")
	private void anyControllerAnnotated() {
	}

	@Pointcut("anyRestControllerAnnotated() || anyControllerAnnotated()")
	private void anyControllerOrRestController() {
	}

	@Around("anyControllerOrRestController()")
	public Object wrapWithCorrelationId(ProceedingJoinPoint pjp) throws Throwable {
		TraceScope scope = trace.startSpan(pjp.toShortString());
		try {
			return pjp.proceed();
		}
		finally {
			scope.close();
		}
	}

	@Pointcut("execution(public * org.springframework.web.client.RestOperations.exchange(..))")
	private void anyExchangeRestOperationsMethod() {
	}

	@Around("anyExchangeRestOperationsMethod()")
	public Object traceRestOperations(ProceedingJoinPoint pjp)
			throws Throwable {
		TraceScope scope = trace.startSpan(pjp.toShortString());
		try {
			String spanId = scope.getSpan().getSpanId();
			String traceId = scope.getSpan().getTraceId();
			log.debug("Wrapping RestTemplate call with trace id [" + traceId + "] and span id [" + spanId + "]");
			HttpEntity httpEntity = findHttpEntity(pjp.getArgs());
			HttpEntity newHttpEntity = createNewHttpEntity(httpEntity, spanId, traceId);
			List<Object> newArgs = modifyHttpEntityInMethodArguments(pjp, newHttpEntity);
			return pjp.proceed();
		}
		finally {
			scope.close();
		}
	}

	protected HttpEntity findHttpEntity(Object[] args) {
		for (Object arg : args) {
			if (isHttpEntity(arg)) {
				return HttpEntity.class.cast(arg);
			}
		}
		return null;
	}

	protected boolean isHttpEntity(Object arg) {
		return HttpEntity.class.isAssignableFrom(arg.getClass());
	}

	@SuppressWarnings("unchecked")
	private HttpEntity createNewHttpEntity(HttpEntity httpEntity, String spanId, String traceId) {
		HttpHeaders newHttpHeaders = new HttpHeaders();
		newHttpHeaders.putAll(httpEntity.getHeaders());
		newHttpHeaders.add(SPAN_ID_NAME, spanId);
		newHttpHeaders.add(TRACE_ID_NAME, traceId);
		return new HttpEntity(httpEntity.getBody(), newHttpHeaders);
	}

	private List<Object> modifyHttpEntityInMethodArguments(ProceedingJoinPoint pjp,
			HttpEntity newHttpEntity) {
		List<Object> newArgs = new ArrayList<>();
		for (Object arg : pjp.getArgs()) {
			if (isHttpEntity(arg)) {
				newArgs.add(newHttpEntity);
			} else {
				newArgs.add(arg);
			}
		}
		return newArgs;
	}
}
