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

package org.springframework.cloud.sleuth.instrument.messaging;

import java.lang.reflect.Method;

import org.apache.commons.logging.Log;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.SpanNameUtil;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.util.ReflectionUtils;

/**
 * Aspect that wraps {@link MessageMapping} annotated methods in a tracing representation.
 *
 * TODO: Document that for client side responders declare them as beans
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
@SuppressWarnings("ArgNamesWarningsInspection")
@Aspect
public class TraceMessagingAspect {

	private static final Log log = org.apache.commons.logging.LogFactory.getLog(TraceMessagingAspect.class);

	static final String MESSAGING_CONTROLLER_CLASS_KEY = "messaging.controller.class";

	static final String MESSAGING_CONTROLLER_METHOD_KEY = "messaging.controller.method";

	private final Tracer tracer;

	private final SpanNamer spanNamer;

	public TraceMessagingAspect(Tracer tracer, SpanNamer spanNamer) {
		this.tracer = tracer;
		this.spanNamer = spanNamer;
	}

	@Pointcut("@within(org.springframework.messaging.handler.annotation.MessageMapping)")
	private void anyMessageMappingAnnotated() {
	} // NOSONAR

	@Around("anyMessageMappingAnnotated()")
	@SuppressWarnings("unchecked")
	public Object addTags(ProceedingJoinPoint pjp) throws Throwable {
		Object object = pjp.proceed();
		String methodName = pjp.getSignature().getName();
		String className = pjp.getTarget().getClass().getName();
		Span currentSpan = currentSpan(pjp);
		currentSpan.tag(MESSAGING_CONTROLLER_CLASS_KEY, className);
		currentSpan.tag(MESSAGING_CONTROLLER_METHOD_KEY, methodName);
		return object;
	}

	private Span currentSpan(ProceedingJoinPoint pjp) {
		Span currentSpan = this.tracer.currentSpan();
		if (currentSpan == null) {
			if (log.isDebugEnabled()) {
				log.debug("No span found - will create a new one");
			}
			currentSpan = this.tracer.nextSpan().name(name(pjp)).start();
		}
		return currentSpan;
	}

	private String name(ProceedingJoinPoint pjp) {
		return this.spanNamer.name(getMethod(pjp, pjp.getTarget()),
				SpanNameUtil.toLowerHyphen(pjp.getSignature().getName()));
	}

	private Method getMethod(ProceedingJoinPoint pjp, Object object) {
		MethodSignature signature = (MethodSignature) pjp.getSignature();
		Method method = signature.getMethod();
		return ReflectionUtils.findMethod(object.getClass(), method.getName(), method.getParameterTypes());
	}

}
