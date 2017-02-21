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

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the {@link SpanCreator} that creates
 * a new span around the annotated method.
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
class DefaultSpanCreator implements SpanCreator {

	private final Tracer tracer;
	private final SpanTagAnnotationHandler annotationSpanUtil;

	DefaultSpanCreator(Tracer tracer,
			SpanTagAnnotationHandler annotationSpanUtil) {
		this.tracer = tracer;
		this.annotationSpanUtil = annotationSpanUtil;
	}

	@Override public Span createSpan(MethodInvocation pjp, NewSpan newSpanAnnotation) {
		String key = StringUtils.isEmpty(newSpanAnnotation.name()) ?
				pjp.getMethod().getName() : newSpanAnnotation.name();
		Span span = createSpan(key);
		this.annotationSpanUtil.addAnnotatedParameters(pjp);
		return span;
	}

	private Span createSpan(String key) {
		if (this.tracer.isTracing()) {
			return this.tracer.createSpan(key, this.tracer.getCurrentSpan());
		}
		return this.tracer.createSpan(key);
	}

}
