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

package org.springframework.cloud.sleuth.instrument.web;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.rest.webmvc.support.DelegatingHandlerMapping;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Bean post processor that wraps Spring Data REST Controllers in named Spans
 *
 * @author Marcin Grzejszczak
 * @since 1.0.3
 */
class TraceSpringDataBeanPostProcessor implements BeanPostProcessor {

	private static final Log log = LogFactory.getLog(TraceSpringDataBeanPostProcessor.class);

	private final BeanFactory beanFactory;

	public TraceSpringDataBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof DelegatingHandlerMapping && !(bean instanceof TraceDelegatingHandlerMapping)) {
			if (log.isDebugEnabled()) {
				log.debug("Wrapping bean [" + beanName + "] of type [" + bean.getClass().getSimpleName() +
						"] in its trace representation");
			}
			return new TraceDelegatingHandlerMapping((DelegatingHandlerMapping) bean,
					this.beanFactory);
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	private static class TraceDelegatingHandlerMapping extends DelegatingHandlerMapping {

		private final DelegatingHandlerMapping delegate;
		private final BeanFactory beanFactory;

		public TraceDelegatingHandlerMapping(DelegatingHandlerMapping delegate,
				BeanFactory beanFactory) {
			super(Collections.<HandlerMapping>emptyList());
			this.delegate = delegate;
			this.beanFactory = beanFactory;
		}

		@Override
		public int getOrder() {
			return this.delegate.getOrder();
		}

		@Override
		public HandlerExecutionChain getHandler(HttpServletRequest request)
				throws Exception {
			HandlerExecutionChain handlerExecutionChain = this.delegate.getHandler(request);
			if (handlerExecutionChain == null) {
				return null;
			}
			handlerExecutionChain.addInterceptor(new TraceHandlerInterceptor(this.beanFactory));
			return handlerExecutionChain;
		}
	}
}
