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

package org.springframework.cloud.sleuth;

import brave.Span;
import brave.Tracer;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

/**
 * Sleuth method invocation processor.
 *
 * @author Marcin Grzejszczak
 */
public abstract class AbstractSleuthMethodInvocationProcessor
		implements BeanFactoryAware {

	private static final Log logger = LogFactory
			.getLog(AbstractSleuthMethodInvocationProcessor.class);

	private static final String CLASS_KEY = "class";

	private static final String METHOD_KEY = "method";

	protected BeanFactory beanFactory;

	private Tracer tracer;

	protected void before(MethodInvocation invocation, Span span) {
		addTags(invocation, span);
	}

	protected void after(Span span, boolean isNewSpan) {
		if (isNewSpan) {
			span.finish();
		}
	}

	protected void onFailure(Span span, Throwable e) {
		if (logger.isDebugEnabled()) {
			logger.debug("Exception occurred while trying to continue the pointcut", e);
		}
		span.error(e);
	}

	private void addTags(MethodInvocation invocation, Span span) {
		span.tag(CLASS_KEY, invocation.getThis().getClass().getSimpleName());
		span.tag(METHOD_KEY, invocation.getMethod().getName());
	}

	protected Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

}
