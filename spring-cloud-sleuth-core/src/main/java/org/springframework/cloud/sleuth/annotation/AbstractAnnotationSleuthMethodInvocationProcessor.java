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

package org.springframework.cloud.sleuth.annotation;

import brave.Span;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.AbstractSleuthMethodInvocationProcessor;

/**
 * Sleuth annotation based method invocation processor.
 *
 * @author Marcin Grzejszczak
 */
abstract class AbstractAnnotationSleuthMethodInvocationProcessor
		extends AbstractSleuthMethodInvocationProcessor
		implements SleuthMethodInvocationProcessor {

	private static final Log logger = LogFactory
			.getLog(AbstractAnnotationSleuthMethodInvocationProcessor.class);

	private SpanTagAnnotationHandler spanTagAnnotationHandler;

	private NewSpanParser newSpanParser;

	protected void before(MethodInvocation invocation, Span span, String log,
			boolean hasLog) {
		super.before(invocation, span);
		spanTagAnnotationHandler().addAnnotatedParameters(invocation);
		if (hasLog) {
			logEvent(span, log + ".before");
		}
	}

	protected void after(Span span, boolean isNewSpan, String log, boolean hasLog) {
		if (hasLog) {
			logEvent(span, log + ".after");
		}
		super.after(span, isNewSpan);
	}

	protected void onFailure(Span span, String log, boolean hasLog, Throwable e) {
		if (hasLog) {
			logEvent(span, log + ".afterFailure");
		}
		super.onFailure(span, e);
	}

	private SpanTagAnnotationHandler spanTagAnnotationHandler() {
		if (this.spanTagAnnotationHandler == null) {
			this.spanTagAnnotationHandler = new SpanTagAnnotationHandler(
					this.beanFactory);
		}
		return this.spanTagAnnotationHandler;
	}

	protected NewSpanParser newSpanParser() {
		if (this.newSpanParser == null) {
			this.newSpanParser = this.beanFactory.getBean(NewSpanParser.class);
		}
		return this.newSpanParser;
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

	protected String log(ContinueSpan continueSpan) {
		if (continueSpan != null) {
			return continueSpan.log();
		}
		return "";
	}

}
