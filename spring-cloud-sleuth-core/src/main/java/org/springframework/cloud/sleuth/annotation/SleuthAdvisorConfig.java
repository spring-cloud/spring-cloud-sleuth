/*
 * Copyright 2013-2018 the original author or authors.
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

import brave.Span;
import brave.Tracer;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
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
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Custom pointcut advisor that picks all classes / interfaces that
 * have the Sleuth related annotations.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
@SuppressWarnings("serial")
class SleuthAdvisorConfig extends AbstractPointcutAdvisor implements BeanFactoryAware {

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
	 * Checks if a class or a method is is annotated with Sleuth related annotations
	 */
	private final class AnnotationClassOrMethodOrArgsPointcut extends
			DynamicMethodMatcherPointcut {

		@Override
		public boolean matches(Method method, Class<?> targetClass, Object... args) {
			return getClassFilter().matches(targetClass);
		}

		@Override public ClassFilter getClassFilter() {
			return new ClassFilter() {
				@Override public boolean matches(Class<?> clazz) {
					return new AnnotationClassOrMethodFilter(NewSpan.class).matches(clazz) ||
							new AnnotationClassOrMethodFilter(ContinueSpan.class).matches(clazz);
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

	/**
	 * Checks if a method is properly annotated with a given Sleuth annotation
	 */
	private static class AnnotationMethodsResolver {

		private final Class<? extends Annotation> annotationType;

		public AnnotationMethodsResolver(Class<? extends Annotation> annotationType) {
			this.annotationType = annotationType;
		}

		public boolean hasAnnotatedMethods(Class<?> clazz) {
			final AtomicBoolean found = new AtomicBoolean(false);
			ReflectionUtils.doWithMethods(clazz, method -> {
				if (found.get()) {
					return;
				}
				Annotation annotation = AnnotationUtils.findAnnotation(method,
						AnnotationMethodsResolver.this.annotationType);
				if (annotation != null) { found.set(true); }
			});
			return found.get();
		}

	}
}

/**
 * Interceptor that creates or continues a span depending on the provided
 * annotation. Also it adds logs and tags if necessary.
 */
class SleuthInterceptor implements IntroductionInterceptor, BeanFactoryAware  {

	private static final Log logger = LogFactory.getLog(SleuthInterceptor.class);
	private static final String CLASS_KEY = "class";
	private static final String METHOD_KEY = "method";

	private BeanFactory beanFactory;
	private NewSpanParser newSpanParser;
	private Tracer tracer;
	private SpanTagAnnotationHandler spanTagAnnotationHandler;

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		Method method = invocation.getMethod();
		if (method == null) {
			return invocation.proceed();
		}

		Method mostSpecificMethod = AopUtils
				.getMostSpecificMethod(method, invocation.getThis().getClass());
		NewSpan newSpan = SleuthAnnotationUtils.findAnnotation(mostSpecificMethod, NewSpan.class);
		ContinueSpan continueSpan = SleuthAnnotationUtils.findAnnotation(mostSpecificMethod, ContinueSpan.class);
		if (newSpan == null && continueSpan == null) {
			return invocation.proceed();
		}

		if(isReactorReturnType(method.getReturnType())){
			return proceedUnderReactorSpan(invocation, newSpan, continueSpan);
		} else {
			return proceedUnderSynchronousSpan(invocation, newSpan, continueSpan);
		}
	}

	private boolean isReactorReturnType(Class<?> returnType) {
		return Flux.class.equals(returnType) || Mono.class.equals(returnType);
	}

	private Object proceedUnderSynchronousSpan(
			MethodInvocation invocation, NewSpan newSpan, ContinueSpan continueSpan) throws Throwable {
		Span span = tracer().currentSpan();
		if (newSpan != null || span == null) {
			span = tracer().nextSpan();
			newSpanParser().parse(invocation, newSpan, span);
			span.start();
		}
		String log = log(continueSpan);
		boolean hasLog = StringUtils.hasText(log);
		try (Tracer.SpanInScope ws = tracer().withSpanInScope(span)) {
			before(invocation, span, log, hasLog);
			return invocation.proceed();
		} catch (Exception e) {
			onFailure(span, log, hasLog, e);
			throw e;
		} finally {
			after(span, newSpan != null, log, hasLog);
		}
	}

	private Object proceedUnderReactorSpan(
			MethodInvocation invocation, NewSpan newSpan, ContinueSpan continueSpan) throws Throwable{
		boolean isNewSpan = newSpan != null;
		Span spanPrevious = tracer().currentSpan();
		Span span;
		if (isNewSpan || spanPrevious == null) {
			span = tracer().nextSpan();
			newSpanParser().parse(invocation, newSpan, span);
		} else {
			span = spanPrevious;
		}

		String log = log(continueSpan);
		boolean hasLog = StringUtils.hasText(log);

		try(Tracer.SpanInScope ws = tracer().withSpanInScope(span)) {

			Publisher<?> publisher = (Publisher) invocation.proceed();

			Mono<Span> startSpan = Mono.defer(() -> withSpanInScope(span, () -> {
				if (isNewSpan || spanPrevious == null) {
					span.start();
				}

				before(invocation, span, log, hasLog);
				return Mono.just(span);
			}));

			if(publisher instanceof Mono){
				return startSpan.flatMap(spanStarted -> ((Mono<?>)publisher)
						.doOnError(onFailureReactive(log, hasLog, spanStarted))
						.doOnTerminate(afterReactive(isNewSpan, log, hasLog, spanStarted)));
			}
			else if(publisher instanceof Flux){
				return startSpan.flatMapMany(spanStarted -> ((Flux<?>)publisher)
						.doOnError(onFailureReactive(log, hasLog, spanStarted))
						.doOnTerminate(afterReactive(isNewSpan, log, hasLog, spanStarted)));
			}
			else {
				throw new IllegalArgumentException("Unexpected type of publisher: "+publisher.getClass());
			}
		}
	}

	private <T> T withSpanInScope(Span span, Supplier<T> supplier) {
		try(Tracer.SpanInScope ws1 = tracer().withSpanInScope(span)) {
			return supplier.get();
		}
	}

	private Runnable afterReactive(boolean isNewSpan, String log, boolean hasLog, Span span) {
		return () -> {
			try(Tracer.SpanInScope ws = tracer().withSpanInScope(span)) {
				after(span, isNewSpan, log, hasLog);
			}
		};
	}

	private Consumer<Throwable> onFailureReactive(String log, boolean hasLog, Span span) {
		return throwable -> {
			try(Tracer.SpanInScope ws = tracer().withSpanInScope(span)) {
				onFailure(span, log, hasLog, throwable);
			}
		};
	}

	private void before(MethodInvocation invocation, Span span, String log, boolean hasLog) {
		if (hasLog) {
			logEvent(span, log + ".before");
		}
		spanTagAnnotationHandler().addAnnotatedParameters(invocation);
		addTags(invocation, span);
	}

	private void after(Span span, boolean isNewSpan, String log, boolean hasLog) {
		if (hasLog) {
			logEvent(span, log + ".after");
		}
		if (isNewSpan) {
			span.finish();
		}
	}

	private void onFailure(Span span, String log, boolean hasLog, Throwable e) {
		if (logger.isDebugEnabled()) {
			logger.debug("Exception occurred while trying to continue the pointcut", e);
		}
		if (hasLog) {
			logEvent(span, log + ".afterFailure");
		}
		span.error(e);
	}

	private void addTags(MethodInvocation invocation, Span span) {
		span.tag(CLASS_KEY, invocation.getThis().getClass().getSimpleName());
		span.tag(METHOD_KEY, invocation.getMethod().getName());
	}

	private void logEvent(Span span, String name) {
		if (span == null) {
			logger.warn("You were trying to continue a span which was null. Please "
					+ "remember that if two proxied methods are calling each other from "
					+ "the same class then the aspect will not be properly resolved");
			return;
		}
		span.annotate(name);
	}

	private String log(ContinueSpan continueSpan) {
		if (continueSpan != null) {
			return continueSpan.log();
		}
		return "";
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	private NewSpanParser newSpanParser() {
		if (this.newSpanParser == null) {
			this.newSpanParser = this.beanFactory.getBean(NewSpanParser.class);
		}
		return this.newSpanParser;
	}

	private SpanTagAnnotationHandler spanTagAnnotationHandler() {
		if (this.spanTagAnnotationHandler == null) {
			this.spanTagAnnotationHandler = new SpanTagAnnotationHandler(this.beanFactory);
		}
		return this.spanTagAnnotationHandler;
	}

	@Override public boolean implementsInterface(Class<?> intf) {
		return true;
	}

	@Override public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}
}
