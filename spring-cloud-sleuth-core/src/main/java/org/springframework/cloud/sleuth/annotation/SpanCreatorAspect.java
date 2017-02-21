/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.annotation;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Due to limitations of Spring AOP we're not creating two separate pointcuts:
 * one for classes annotated with the {@link NewSpan} annotation and one
 * for methods annotated with that annotation cause it will not pick interfaces
 * that have annotated methods.
 *
 * This advice is not registered in Spring context. We will pass it and register
 * it manually when necessary. That's why we don't have to worry about the
 * performance issue of the "advise all public methods" pointcut.
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
@Aspect
class SpanCreatorAspect {

	private final SpanCreator spanCreator;
	private final Tracer tracer;

	SpanCreatorAspect(SpanCreator spanCreator, Tracer tracer) {
		this.spanCreator = spanCreator;
		this.tracer = tracer;
	}

	/**
	 * Not creating two separate pointcuts due to the fact that it's not possible
	 * to create a pointcut for interfaces with annotated methods.
	 */
	@Pointcut("execution(public * *(..))")
	private void anyPublicOperation() {
	}

	@Around("anyPublicOperation()")
	public Object instrumentOnMethodAnnotation(ProceedingJoinPoint pjp) throws Throwable {
		Method method = getMethod(pjp);
		if (method == null) {
			return pjp.proceed();
		}
		Method mostSpecificMethod = AopUtils
				.getMostSpecificMethod(method, pjp.getTarget().getClass());
		NewSpan annotation = SleuthAnnotationUtils.findAnnotation(mostSpecificMethod);
		if (annotation == null) {
			return pjp.proceed();
		}
		Span span = null;
		try {
			span = this.spanCreator.createSpan(pjp, annotation);
			return pjp.proceed();
		} finally {
			if (span != null) {
				this.tracer.close(span);
			}
		}
	}
	
	private Method getMethod(ProceedingJoinPoint pjp) {
		Signature signature = pjp.getStaticPart().getSignature();
		if (signature instanceof MethodSignature) {
			MethodSignature methodSignature = (MethodSignature) signature;
			return methodSignature.getMethod();
		}
		return null;
	}

}
