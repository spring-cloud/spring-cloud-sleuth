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

package org.springframework.cloud.sleuth.util;

import org.springframework.cloud.sleuth.SpanName;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * Utility class that tries to get the Span name from
 * SpanName annotation value if one is present. If that's not the case
 * then it delegates to toString() method of the object
 *
 * @see org.springframework.cloud.sleuth.SpanName
 *
 * @author Marcin Grzejszczak
 */
public class SpanNameRetrievalUtil {

	public static String getSpanName(Object object) {
		SpanName annotation = AnnotationUtils
				.findAnnotation(object.getClass(), SpanName.class);
		return annotation != null ? annotation.value() : object.toString();
	}
}
