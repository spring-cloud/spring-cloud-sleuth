/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.session;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.util.ReflectionUtils;

/**
 * Aspect around {@link SessionRepository} and {@link ReactiveSessionRepository} method
 * execution.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
@Aspect
public class TraceSessionRepositoryAspect {

	private static final Log log = LogFactory.getLog(TraceSessionRepositoryAspect.class);

	private final Tracer tracer;

	private final CurrentTraceContext currentTraceContext;

	public TraceSessionRepositoryAspect(Tracer tracer, CurrentTraceContext currentTraceContext) {
		this.tracer = tracer;
		this.currentTraceContext = currentTraceContext;
	}

	// RedisIndexedSessionRepository
	@Around("execution(public * org.springframework.session.SessionRepository.*(..))")
	public Object wrapSessionRepository(ProceedingJoinPoint pjp) throws Throwable {
		SessionRepository target = (SessionRepository) pjp.getTarget();
		if (target instanceof TraceSessionRepository) {
			return pjp.proceed();
		}
		target = wrapSessionRepository(target);
		return callMethodOnWrappedObject(pjp, target);
	}

	private SessionRepository wrapSessionRepository(SessionRepository target) {
		if (target instanceof FindByIndexNameSessionRepository) {
			return new TraceFindByIndexNameSessionRepository(this.tracer, (FindByIndexNameSessionRepository) target);
		}
		return new TraceSessionRepository(this.tracer, target);
	}

	private <T> Object callMethodOnWrappedObject(ProceedingJoinPoint pjp, T target) throws Throwable {
		Method method = getMethod(pjp, target);
		if (method != null) {
			if (log.isDebugEnabled()) {
				log.debug("Found a corresponding method on the trace representation [" + method + "]");
			}
			try {
				return method.invoke(target, pjp.getArgs());
			} catch(InvocationTargetException ex) {
				throw ex.getCause();
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("Method [" + pjp.getSignature().getName()
					+ "] not found on the trace representation. Will run the original one.");
		}
		return pjp.proceed();
	}

	@Around("execution(public * org.springframework.session.ReactiveSessionRepository.*(..))")
	public Object wrapReactiveSessionRepository(ProceedingJoinPoint pjp) throws Throwable {
		ReactiveSessionRepository target = (ReactiveSessionRepository) pjp.getTarget();
		if (target instanceof TraceReactiveSessionRepository) {
			return pjp.proceed();
		}
		target = new TraceReactiveSessionRepository(this.tracer, this.currentTraceContext, target);
		return callMethodOnWrappedObject(pjp, target);
	}

	private Method getMethod(ProceedingJoinPoint pjp, Object tracingWrapper) {
		MethodSignature signature = (MethodSignature) pjp.getSignature();
		Method method = signature.getMethod();
		Method foundMethodOnTracingWrapper = ReflectionUtils.findMethod(tracingWrapper.getClass(), method.getName(),
				method.getParameterTypes());
		if (foundMethodOnTracingWrapper != null) {
			if (log.isDebugEnabled()) {
				log.debug("Found an exact match for method execution [" + foundMethodOnTracingWrapper + "]");
			}
			return foundMethodOnTracingWrapper;
		}
		Method[] uniquePublicDeclaredMethodsOnTracingWrapper = ReflectionUtils
				.getUniqueDeclaredMethods(tracingWrapper.getClass(), m -> Modifier.isPublic(m.getModifiers()));
		if (uniquePublicDeclaredMethodsOnTracingWrapper.length == 0) {
			return null;
		}
		if (log.isTraceEnabled()) {
			log.trace("Will pick one of the unique declared methods ["
					+ Arrays.toString(uniquePublicDeclaredMethodsOnTracingWrapper) + "] that has a name ["
					+ method.getName() + "]");
		}
		Object[] argsOnOriginalObject = pjp.getArgs();
		return Arrays.stream(uniquePublicDeclaredMethodsOnTracingWrapper)
				.filter(m -> m.getName().equals(method.getName())
						&& paramsAreOfSameTyperInherited(argsOnOriginalObject, m.getParameterTypes()))
				.findFirst().orElse(null);
	}

	private boolean paramsAreOfSameTyperInherited(Object[] argsOnOriginalObject, Class<?>[] typeOnTracingWrapper) {
		if (argsOnOriginalObject.length != typeOnTracingWrapper.length) {
			return false;
		}
		for (int i = 0; i < argsOnOriginalObject.length; i++) {
			Class<?> argType = argsOnOriginalObject[i].getClass();
			Class<?> typeOnWrapper = typeOnTracingWrapper[i];
			if (!typeOnWrapper.isAssignableFrom(argType)) {
				return false;
			}
		}
		return true;
	}

}
