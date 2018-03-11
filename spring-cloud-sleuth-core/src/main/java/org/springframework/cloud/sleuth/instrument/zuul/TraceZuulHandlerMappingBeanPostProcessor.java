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

package org.springframework.cloud.sleuth.instrument.zuul;

import java.lang.invoke.MethodHandles;

import brave.spring.webmvc.SpanCustomizingAsyncHandlerInterceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.netflix.zuul.web.ZuulHandlerMapping;

/**
 * Bean post processor that wraps {@link ZuulHandlerMapping} in its
 * trace representation.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.3
 */
class TraceZuulHandlerMappingBeanPostProcessor implements BeanPostProcessor {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final BeanFactory beanFactory;

	public TraceZuulHandlerMappingBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof ZuulHandlerMapping) {
			if (log.isDebugEnabled()) {
				log.debug("Attaching trace interceptor to bean [" + beanName + "] of type [" + bean.getClass().getSimpleName() + "]");
			}
			ZuulHandlerMapping zuulHandlerMapping = (ZuulHandlerMapping) bean;
			zuulHandlerMapping.setInterceptors(
					this.beanFactory.getBean(SpanCustomizingAsyncHandlerInterceptor.class));
		}
		return bean;
	}
}
