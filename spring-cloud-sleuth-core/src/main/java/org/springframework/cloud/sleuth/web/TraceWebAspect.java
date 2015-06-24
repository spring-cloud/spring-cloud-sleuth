package org.springframework.cloud.sleuth.web;

import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.apachecommons.CommonsLog;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Pointcut;
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

	private static final int HTTP_ENTITY_PARAM_INDEX = 2;

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
	public Object wrapWithCorrelationIdForRestOperations(ProceedingJoinPoint pjp)
			throws Throwable {
		TraceScope scope = trace.startSpan(pjp.toShortString());
		try {
			String spanId = scope.getSpan().getSpanId();
			//TODO: set traceId on restTemplate call as well
			log.debug("Wrapping RestTemplate call with span id [" + spanId + "]");
			HttpEntity httpEntity = (HttpEntity) pjp.getArgs()[HTTP_ENTITY_PARAM_INDEX];
			HttpEntity newHttpEntity = createNewHttpEntity(httpEntity, spanId);
			List<Object> newArgs = modifyHttpEntityInMethodArguments(pjp, newHttpEntity);
			return pjp.proceed(newArgs.toArray());
		}
		finally {
			scope.close();
		}
	}

	@SuppressWarnings("unchecked")
	private HttpEntity createNewHttpEntity(HttpEntity httpEntity, String correlationId) {
		HttpHeaders newHttpHeaders = new HttpHeaders();
		newHttpHeaders.putAll(httpEntity.getHeaders());
		newHttpHeaders.add(SPAN_ID_NAME, correlationId);
		return new HttpEntity(httpEntity.getBody(), newHttpHeaders);
	}

	private List<Object> modifyHttpEntityInMethodArguments(ProceedingJoinPoint pjp,
			HttpEntity newHttpEntity) {
		List<Object> newArgs = new ArrayList<>();
		for (int i = 0; i < pjp.getArgs().length; i++) {
			Object arg = pjp.getArgs()[i];
			if (i != HTTP_ENTITY_PARAM_INDEX) {
				newArgs.add(i, arg);
			}
			else {
				newArgs.add(i, newHttpEntity);
			}
		}
		return newArgs;
	}
}
