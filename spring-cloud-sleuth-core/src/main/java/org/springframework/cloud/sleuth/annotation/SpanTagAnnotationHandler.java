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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import brave.SpanCustomizer;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.StringUtils;

/**
 * This class is able to find all methods annotated with the
 * Sleuth annotations. All methods mean that if you have both an interface
 * and an implementation annotated with Sleuth annotations then this class is capable
 * of finding both of them and merging into one set of tracing information.
 *
 * This information is then used to add proper tags to the span from the
 * method arguments that are annotated with {@link SpanTag}.
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
class SpanTagAnnotationHandler {

	private static final Log log = LogFactory.getLog(SpanTagAnnotationHandler.class);

	private final BeanFactory beanFactory;
	private SpanCustomizer spanCustomizer;
	
	SpanTagAnnotationHandler(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	void addAnnotatedParameters(MethodInvocation pjp) {
		try {
			Method method = pjp.getMethod();
			Method mostSpecificMethod = AopUtils.getMostSpecificMethod(method,
					pjp.getThis().getClass());
			List<SleuthAnnotatedParameter> annotatedParameters =
					SleuthAnnotationUtils.findAnnotatedParameters(mostSpecificMethod, pjp.getArguments());
			getAnnotationsFromInterfaces(pjp, mostSpecificMethod, annotatedParameters);
			mergeAnnotatedMethodsIfNecessary(pjp, method, mostSpecificMethod,
					annotatedParameters);
			addAnnotatedArguments(annotatedParameters);
		} catch (SecurityException e) {
			log.error("Exception occurred while trying to add annotated parameters", e);
		}
	}

	private void getAnnotationsFromInterfaces(MethodInvocation pjp,
			Method mostSpecificMethod,
			List<SleuthAnnotatedParameter> annotatedParameters) {
		Class<?>[] implementedInterfaces = pjp.getThis().getClass().getInterfaces();
		if (implementedInterfaces.length > 0) {
			for (Class<?> implementedInterface : implementedInterfaces) {
				for (Method methodFromInterface : implementedInterface.getMethods()) {
					if (methodsAreTheSame(mostSpecificMethod, methodFromInterface)) {
						List<SleuthAnnotatedParameter> annotatedParametersForActualMethod =
								SleuthAnnotationUtils.findAnnotatedParameters(methodFromInterface, pjp.getArguments());
						mergeAnnotatedParameters(annotatedParameters, annotatedParametersForActualMethod);
					}
				}
			}
		}
	}

	private boolean methodsAreTheSame(Method mostSpecificMethod, Method method1) {
		return method1.getName().equals(mostSpecificMethod.getName()) &&
				Arrays.equals(method1.getParameterTypes(), mostSpecificMethod.getParameterTypes());
	}

	private void mergeAnnotatedMethodsIfNecessary(MethodInvocation pjp, Method method,
			Method mostSpecificMethod, List<SleuthAnnotatedParameter> annotatedParameters) {
		// that can happen if we have an abstraction and a concrete class that is
		// annotated with @NewSpan annotation
		if (!method.equals(mostSpecificMethod)) {
			List<SleuthAnnotatedParameter> annotatedParametersForActualMethod = SleuthAnnotationUtils.findAnnotatedParameters(
					method, pjp.getArguments());
			mergeAnnotatedParameters(annotatedParameters, annotatedParametersForActualMethod);
		}
	}

	private void mergeAnnotatedParameters(List<SleuthAnnotatedParameter> annotatedParametersIndices,
			List<SleuthAnnotatedParameter> annotatedParametersIndicesForActualMethod) {
		for (SleuthAnnotatedParameter container : annotatedParametersIndicesForActualMethod) {
			final int index = container.parameterIndex;
			boolean parameterContained = false;
			for (SleuthAnnotatedParameter parameterContainer : annotatedParametersIndices) {
				if (parameterContainer.parameterIndex == index) {
					parameterContained = true;
					break;
				}
			}
			if (!parameterContained) {
				annotatedParametersIndices.add(container);
			}
		}
	}

	private void addAnnotatedArguments(List<SleuthAnnotatedParameter> toBeAdded) {
		for (SleuthAnnotatedParameter container : toBeAdded) {
			String tagValue = resolveTagValue(container.annotation, container.argument);
			String tagKey = resolveTagKey(container);
			span().tag(tagKey, tagValue);
		}
	}

	private SpanCustomizer span() {
		if (this.spanCustomizer == null) {
			this.spanCustomizer = this.beanFactory.getBean(SpanCustomizer.class);
		}
		return this.spanCustomizer;
	}


	private String resolveTagKey(
			SleuthAnnotatedParameter container) {
		return StringUtils.hasText(container.annotation.value()) ?
				container.annotation.value() : container.annotation.key();
	}

	String resolveTagValue(SpanTag annotation, Object argument) {
		if (argument == null) {
			return "";
		}
		if (annotation.resolver() != NoOpTagValueResolver.class) {
			TagValueResolver tagValueResolver = this.beanFactory.getBean(annotation.resolver());
			return tagValueResolver.resolve(argument);
		} else if (StringUtils.hasText(annotation.expression())) {
			return this.beanFactory.getBean(TagValueExpressionResolver.class)
					.resolve(annotation.expression(), argument);
		}
		return argument.toString();
	}
}
