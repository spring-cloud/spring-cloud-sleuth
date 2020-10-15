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

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;

/**
 * Sleuth annotation processor.
 *
 * @author Marcin Grzejszczak
 */
abstract class AbstractSleuthMethodInvocationProcessor implements SleuthMethodInvocationProcessor, BeanFactoryAware {

	private static final Log logger = LogFactory.getLog(AbstractSleuthMethodInvocationProcessor.class);

	private static final String CLASS_KEY = "class";

	private static final String METHOD_KEY = "method";

	BeanFactory beanFactory;

	private NewSpanParser newSpanParser;

	private Tracer tracer;

	private CurrentTraceContext currentTraceContext;

	private SpanTagAnnotationHandler spanTagAnnotationHandler;

	void before(MethodInvocation invocation, Span span, String log, boolean hasLog) {
		if (hasLog) {
			logEvent(span, log + ".before");
		}
		spanTagAnnotationHandler().addAnnotatedParameters(invocation);
		addTags(invocation, span);
	}

	void after(Span span, boolean isNewSpan, String log, boolean hasLog) {
		if (hasLog) {
			logEvent(span, log + ".after");
		}
		if (isNewSpan) {
			span.end();
		}
	}

	void onFailure(Span span, String log, boolean hasLog, Throwable e) {
		if (logger.isDebugEnabled()) {
			logger.debug("Exception occurred while trying to continue the pointcut", e);
		}
		if (hasLog) {
			logEvent(span, log + ".afterFailure");
		}
		span.error(e);
	}

	void addTags(MethodInvocation invocation, Span span) {
		span.tag(CLASS_KEY, invocation.getThis().getClass().getSimpleName());
		span.tag(METHOD_KEY, invocation.getMethod().getName());
	}

	void logEvent(Span span, String name) {
		if (span == null) {
			logger.warn("You were trying to continue a span which was null. Please "
					+ "remember that if two proxied methods are calling each other from "
					+ "the same class then the aspect will not be properly resolved");
			return;
		}
		span.event(name);
	}

	String log(ContinueSpan continueSpan) {
		if (continueSpan != null) {
			return continueSpan.log();
		}
		return "";
	}

	Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	CurrentTraceContext currentTraceContext() {
		if (this.currentTraceContext == null) {
			this.currentTraceContext = this.beanFactory.getBean(CurrentTraceContext.class);
		}
		return this.currentTraceContext;
	}

	NewSpanParser newSpanParser() {
		if (this.newSpanParser == null) {
			this.newSpanParser = this.beanFactory.getBean(NewSpanParser.class);
		}
		return this.newSpanParser;
	}

	SpanTagAnnotationHandler spanTagAnnotationHandler() {
		if (this.spanTagAnnotationHandler == null) {
			this.spanTagAnnotationHandler = new SpanTagAnnotationHandler(this.beanFactory);
		}
		return this.spanTagAnnotationHandler;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

}
