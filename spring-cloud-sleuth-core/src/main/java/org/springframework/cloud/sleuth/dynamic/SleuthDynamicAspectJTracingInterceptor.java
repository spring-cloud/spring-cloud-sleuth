/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.dynamic;

import brave.Span;
import brave.Tracer;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.cloud.sleuth.AbstractSleuthMethodInvocationProcessor;
import org.springframework.cloud.sleuth.util.SpanNameUtil;

/**
 * Interceptor for tracing {@link MethodInvocation} objects.
 *
 * @author Taras Danylchuk
 * @since 2.2.0
 */
public class SleuthDynamicAspectJTracingInterceptor extends
		AbstractSleuthMethodInvocationProcessor implements IntroductionInterceptor {

	private static final String SPAN_NAME_DELIMITER = "::";

	private static final String METHOD_PARAMETER_PREFIX = "method_parameter_";

	private final boolean isTraceParameters;

	SleuthDynamicAspectJTracingInterceptor(boolean isTraceParameters) {
		this.isTraceParameters = isTraceParameters;
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		Span span = startNewSpan(invocation);
		try (Tracer.SpanInScope ws = tracer().withSpanInScope(span)) {
			before(invocation, span);
			return invocation.proceed();
		}
		catch (Exception ex) {
			onFailure(span, ex);
			throw ex;
		}
		finally {
			after(span, true);
		}
	}

	private Span startNewSpan(MethodInvocation joinPoint) {
		Span span = tracer().nextSpan();
		String methodName = joinPoint.getMethod().getName();
		Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getThis());
		String spanName = SpanNameUtil
				.toLowerHyphen(getSpanName(methodName, targetClass));
		span.name(spanName);

		if (this.isTraceParameters) {
			Object[] args = joinPoint.getArguments();
			if (args != null) {
				for (int i = 0; i < args.length; i++) {
					span.tag(METHOD_PARAMETER_PREFIX + i, String.valueOf(args[i]));
				}
			}
		}
		span.start();
		return span;
	}

	private String getSpanName(String methodName, Class<?> targetClass) {
		return String.join(SPAN_NAME_DELIMITER, targetClass.getSimpleName(), methodName);
	}

	@Override
	public boolean implementsInterface(Class<?> intf) {
		return true;
	}

}
