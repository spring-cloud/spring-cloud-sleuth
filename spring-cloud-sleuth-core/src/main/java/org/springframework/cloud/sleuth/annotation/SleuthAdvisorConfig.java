/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DynamicMethodMatcherPointcut;
import org.springframework.aop.support.annotation.AnnotationClassFilter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Custom pointcut advisor that picks all classes / interfaces that have the Sleuth
 * related annotations.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
@SuppressWarnings("serial")
public class SleuthAdvisorConfig extends AbstractPointcutAdvisor implements BeanFactoryAware {

	private Advice advice;

	private Pointcut pointcut;

	private BeanFactory beanFactory;

	@PostConstruct
	public void init() {
		this.pointcut = buildPointcut();
		this.advice = buildAdvice();
		if (this.advice instanceof BeanFactoryAware) {
			((BeanFactoryAware) this.advice).setBeanFactory(this.beanFactory);
		}
	}

	/**
	 * Set the {@code BeanFactory} to be used when looking up executors by qualifier.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Advice getAdvice() {
		return this.advice;
	}

	@Override
	public Pointcut getPointcut() {
		return this.pointcut;
	}

	private Advice buildAdvice() {
		return new SleuthInterceptor();
	}

	private Pointcut buildPointcut() {
		return new AnnotationClassOrMethodOrArgsPointcut();
	}

	/**
	 * Checks if a method is properly annotated with a given Sleuth annotation.
	 */
	private static class AnnotationMethodsResolver {

		private final Class<? extends Annotation> annotationType;

		AnnotationMethodsResolver(Class<? extends Annotation> annotationType) {
			this.annotationType = annotationType;
		}

		boolean hasAnnotatedMethods(Class<?> clazz) {
			final AtomicBoolean found = new AtomicBoolean(false);
			ReflectionUtils.doWithMethods(clazz, (method -> {
				if (found.get()) {
					return;
				}
				Annotation annotation = AnnotationUtils.findAnnotation(method,
						AnnotationMethodsResolver.this.annotationType);
				if (annotation != null) {
					found.set(true);
				}
			}));
			return found.get();
		}

	}

	/**
	 * Checks if a class or a method is is annotated with Sleuth related annotations.
	 */
	private final class AnnotationClassOrMethodOrArgsPointcut extends DynamicMethodMatcherPointcut {

		@Override
		public boolean matches(Method method, Class<?> targetClass, Object... args) {
			// Skip check here as actual check takes place in
			// SleuthInterceptor.invoke(MethodInvocation)
			return true;
		}

		@Override
		public ClassFilter getClassFilter() {
			return new ClassFilter() {
				@Override
				public boolean matches(Class<?> clazz) {
					return new AnnotationClassOrMethodFilter(NewSpan.class).matches(clazz)
							|| new AnnotationClassOrMethodFilter(ContinueSpan.class).matches(clazz);
				}
			};
		}

	}

	private final class AnnotationClassOrMethodFilter extends AnnotationClassFilter {

		private final AnnotationMethodsResolver methodResolver;

		AnnotationClassOrMethodFilter(Class<? extends Annotation> annotationType) {
			super(annotationType, true);
			this.methodResolver = new AnnotationMethodsResolver(annotationType);
		}

		@Override
		public boolean matches(Class<?> clazz) {
			return super.matches(clazz) || this.methodResolver.hasAnnotatedMethods(clazz);
		}

	}

}

/**
 * Interceptor that creates or continues a span depending on the provided annotation. Also
 * it adds logs and tags if necessary.
 *
 * @author Marcin Grzejszczak
 */
class SleuthInterceptor implements IntroductionInterceptor, BeanFactoryAware {

	private BeanFactory beanFactory;

	private SleuthMethodInvocationProcessor methodInvocationProcessor;

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		Method method = invocation.getMethod();
		if (method == null) {
			return invocation.proceed();
		}
		Method mostSpecificMethod = AopUtils.getMostSpecificMethod(method, invocation.getThis().getClass());
		NewSpan newSpan = SleuthAnnotationUtils.findAnnotation(mostSpecificMethod, NewSpan.class);
		ContinueSpan continueSpan = SleuthAnnotationUtils.findAnnotation(mostSpecificMethod, ContinueSpan.class);
		if (newSpan == null && continueSpan == null) {
			return invocation.proceed();
		}
		return methodInvocationProcessor().process(invocation, newSpan, continueSpan);
	}

	private SleuthMethodInvocationProcessor methodInvocationProcessor() {
		if (this.methodInvocationProcessor == null) {
			this.methodInvocationProcessor = this.beanFactory.getBean(SleuthMethodInvocationProcessor.class);
		}
		return this.methodInvocationProcessor;
	}

	@Override
	public boolean implementsInterface(Class<?> intf) {
		return true;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

}
