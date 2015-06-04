/*
 * Copyright 2012-2015 the original author or authors.
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
package org.springframework.cloud.sleuth.correlation;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestOperations;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.springframework.cloud.sleuth.correlation.CorrelationIdHolder.CORRELATION_ID_HEADER;

/**
 * Aspect that adds correlation id to
 * <p/>
 * <ul>
 * <li>{@link RestController} annotated classes
 * with public {@link Callable} methods</li>
 * <li>{@link Controller} annotated classes
 * with public {@link Callable} methods</li>
 * <li>explicit {@link RestOperations}.exchange(..) method calls</li>
 * </ul>
 * <p/>
 * For controllers an around aspect is created that wraps the {@link Callable#call()} method execution
 * in {@link CorrelationIdUpdater#wrapCallableWithId(Callable)}
 * <p/>
 * For {@link RestOperations} we are wrapping all executions of the
 * <b>exchange</b> methods and we are extracting {@link HttpHeaders} from the passed {@link HttpEntity}.
 * Next we are adding correlation id header {@link CorrelationIdHolder#CORRELATION_ID_HEADER} with
 * the value taken from {@link CorrelationIdHolder}. Finally the method execution proceeds.
 *
 * @see RestController
 * @see Controller
 * @see RestOperations
 * @see CorrelationIdHolder
 * @see CorrelationIdFilter
 *
 * @author Tomasz Nurkewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 */
@Aspect
public class CorrelationIdAspect {
	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int HTTP_ENTITY_PARAM_INDEX = 2;

	@Pointcut("@target(org.springframework.web.bind.annotation.RestController)")
	private void anyRestControllerAnnotated() {
	}

	@Pointcut("@target(org.springframework.stereotype.Controller)")
	private void anyControllerAnnotated() {
	}

	@Pointcut("execution(public java.util.concurrent.Callable *(..))")
	private void anyPublicMethodReturningCallable() {
	}

	@Pointcut("(anyRestControllerAnnotated() || anyControllerAnnotated()) && anyPublicMethodReturningCallable()")
	private void anyControllerOrRestControllerWithPublicAsyncMethod() {
	}

	@Around("anyControllerOrRestControllerWithPublicAsyncMethod()")
	public Object wrapWithCorrelationId(ProceedingJoinPoint pjp) throws Throwable {
		final Callable callable = (Callable) pjp.proceed();
		log.debug("Wrapping callable with correlation id [" + CorrelationIdHolder.get() + "]");
		return CorrelationIdUpdater.wrapCallableWithId(new Callable() {
			@Override
			public Object call() throws Exception {
				return callable.call();
			}
		});
	}

	@Pointcut("execution(public * org.springframework.web.client.RestOperations.exchange(..))")
	private void anyExchangeRestOperationsMethod() {
	}

	@Around("anyExchangeRestOperationsMethod()")
	public Object wrapWithCorrelationIdForRestOperations(ProceedingJoinPoint pjp) throws Throwable {
		String correlationId = CorrelationIdHolder.get();
		log.debug("Wrapping RestTemplate call with correlation id [" + correlationId + "]");
		HttpEntity httpEntity = (HttpEntity) pjp.getArgs()[HTTP_ENTITY_PARAM_INDEX];
		HttpEntity newHttpEntity = createNewHttpEntity(httpEntity, correlationId);
		List<Object> newArgs = modifyHttpEntityInMethodArguments(pjp, newHttpEntity);
		return pjp.proceed(newArgs.toArray());
	}

	@SuppressWarnings("unchecked")
	private HttpEntity createNewHttpEntity(HttpEntity httpEntity, String correlationId) {
		HttpHeaders newHttpHeaders = new HttpHeaders();
		newHttpHeaders.putAll(httpEntity.getHeaders());
		newHttpHeaders.add(CORRELATION_ID_HEADER, correlationId);
		return new HttpEntity(httpEntity.getBody(), newHttpHeaders);
	}

	private List<Object> modifyHttpEntityInMethodArguments(ProceedingJoinPoint pjp, HttpEntity newHttpEntity) {
		List<Object> newArgs = new ArrayList<>();
		for (int i = 0; i < pjp.getArgs().length; i++) {
			Object arg = pjp.getArgs()[i];
			if (i != HTTP_ENTITY_PARAM_INDEX) {
				newArgs.add(i, arg);
			} else {
				newArgs.add(i, newHttpEntity);
			}
		}
		return newArgs;
	}
}
