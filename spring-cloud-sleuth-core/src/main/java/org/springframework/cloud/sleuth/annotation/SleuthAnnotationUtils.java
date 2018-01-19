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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * Utility class that can verify whether the method is annotated with
 * the Sleuth annotations.
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
class SleuthAnnotationUtils {

	private static final Log log = LogFactory.getLog(SleuthAnnotationUtils.class);

	static boolean isMethodAnnotated(Method method) {
		return findAnnotation(method, NewSpan.class) != null ||
				findAnnotation(method, ContinueSpan.class) != null;
	}

	static boolean hasAnnotatedParams(Method method, Object[] args) {
		return !findAnnotatedParameters(method, args).isEmpty();
	}

	static List<SleuthAnnotatedParameter> findAnnotatedParameters(Method method, Object[] args) {
		Annotation[][] parameters = method.getParameterAnnotations();
		List<SleuthAnnotatedParameter> result = new ArrayList<>();
		int i = 0;
		for (Annotation[] parameter : parameters) {
			for (Annotation parameter2 : parameter) {
				if (parameter2 instanceof SpanTag) {
					result.add(new SleuthAnnotatedParameter(i, (SpanTag) parameter2, args[i]));
				}
			}
			i++;
		}
		return result;
	}

	/**
	 * Searches for an annotation either on a method or inside the method parameters
	 */
	static <T extends Annotation> T findAnnotation(Method method, Class<T> clazz) {
		T annotation = AnnotationUtils.findAnnotation(method, clazz);
		if (annotation == null) {
			try {
				annotation = AnnotationUtils.findAnnotation(
						method.getDeclaringClass().getMethod(method.getName(),
								method.getParameterTypes()), clazz);
			} catch (NoSuchMethodException | SecurityException e) {
				if (log.isDebugEnabled()) {
					log.debug("Exception occurred while tyring to find the annotation", e);
				}
			}
		}
		return annotation;
	}
}
