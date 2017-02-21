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

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

/**
 * This class is able to find all methods annotated with the
 * {@link NewSpan} annotation. All methods mean that if you have both an interface
 * and an implementation annotated with {@link NewSpan} then this class is capable
 * of finding both of them and merging into one set of tracing information.
 *
 * This information is then used to add proper tags to the span from the
 * method arguments that are annotated with {@link SpanTag}.
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
class SpanTagAnnotationHandler {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final ApplicationContext context;
	private final Tracer tracer;
	
	SpanTagAnnotationHandler(ApplicationContext context, Tracer tracer) {
		this.context = context;
		this.tracer = tracer;
	}

	void addAnnotatedParameters(JoinPoint pjp) {
		try {
			Signature signature = pjp.getStaticPart().getSignature();
			if (signature instanceof MethodSignature) {
				MethodSignature ms = (MethodSignature) signature;
				Method method = ms.getMethod();
				Method mostSpecificMethod = AopUtils.getMostSpecificMethod(method,
						pjp.getTarget().getClass());
				List<SleuthAnnotatedParameter> annotatedParameters =
						findAnnotatedParameters(mostSpecificMethod, pjp.getArgs());
				mergeAnnotatedMethodsIfNecessary(pjp, method, mostSpecificMethod,
						annotatedParameters);
				addAnnotatedArguments(annotatedParameters);
			}
		} catch (SecurityException e) {
			log.error("Exception occurred while trying to add annotated parameters", e);
		}
	}

	private void mergeAnnotatedMethodsIfNecessary(JoinPoint pjp, Method method,
			Method mostSpecificMethod, List<SleuthAnnotatedParameter> annotatedParameters) {
		// that can happen if we have an abstraction and a concrete class that is
		// annotated with @NewSpan annotation
		if (!method.equals(mostSpecificMethod)) {
			List<SleuthAnnotatedParameter> annotatedParametersForActualMethod = findAnnotatedParameters(
					method, pjp.getArgs());
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
			if (container.isSpanTag()) {
				SpanTag spanTag = (SpanTag) container.annotation;
				String tagValue = resolveTagValue(spanTag, container.argument);
				this.tracer.addTag(spanTag.value(), tagValue);
			}
		}
	}

	String resolveTagValue(SpanTag annotation, Object argument) {
		if (argument == null) {
			return "";
		}
		if (StringUtils.hasText(annotation.tagValueResolverBeanName())) {
			// Resolve via custom impl of Sleuth Tag Value Resolver
			SleuthTagValueResolver tagValueResolver =
					this.context.getBean(annotation.tagValueResolverBeanName(), SleuthTagValueResolver.class);
			return tagValueResolver.resolveTagValue(argument);
		} else if (StringUtils.hasText(annotation.tagValueExpression())) {
			// SPEL
			try {
				ExpressionParser expressionParser = new SpelExpressionParser();
				Expression expression = expressionParser.parseExpression(annotation.tagValueExpression());
				return expression.getValue(argument, String.class);
			} catch (Exception e) {
				log.error("Exception occurred while tying to evaluate the SPEL expression [" + annotation.tagValueExpression() + "]", e);
			}
		}
		return argument.toString();
	}

	private List<SleuthAnnotatedParameter> findAnnotatedParameters(Method method, Object[] args) {
		Annotation[][] parameters = method.getParameterAnnotations();
		List<SleuthAnnotatedParameter> result = new ArrayList<>();
		int i = 0;
		for (Annotation[] parameter : parameters) {
			for (Annotation parameter2 : parameter) {
				if (parameter2 instanceof SpanTag) {
					result.add(new SleuthAnnotatedParameter(i, parameter2, args[i]));
				}
			}
			i++;
		}
		return result;
	}

}
