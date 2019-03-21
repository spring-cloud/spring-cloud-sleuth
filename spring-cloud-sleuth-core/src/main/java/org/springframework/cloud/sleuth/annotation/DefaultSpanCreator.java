/*
 * Copyright 2013-2016 the original author or authors.
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

import java.lang.invoke.MethodHandles;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.util.SpanNameUtil;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the {@link SpanCreator} that creates
 * a new span around the annotated method.
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
class DefaultSpanCreator implements SpanCreator {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final Tracer tracer;

	DefaultSpanCreator(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override public Span createSpan(MethodInvocation pjp, NewSpan newSpanAnnotation) {
		String name = StringUtils.isEmpty(newSpanAnnotation.name()) ?
				pjp.getMethod().getName() : newSpanAnnotation.name();
		String changedName = SpanNameUtil.toLowerHyphen(name);
		if (log.isDebugEnabled()) {
			log.debug("For the class [" + pjp.getThis().getClass() + "] method "
					+ "[" + pjp.getMethod().getName() + "] will name the span [" + changedName + "]");
		}
		return createSpan(changedName);
	}

	private Span createSpan(String name) {
		if (this.tracer.isTracing()) {
			return this.tracer.createSpan(name, this.tracer.getCurrentSpan());
		}
		return this.tracer.createSpan(name);
	}

}
