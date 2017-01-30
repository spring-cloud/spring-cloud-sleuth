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

package org.springframework.cloud.sleuth.instrument.zuul;

import java.lang.invoke.MethodHandles;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.cloud.netflix.zuul.web.ZuulController;
import org.springframework.cloud.netflix.zuul.web.ZuulHandlerMapping;
import org.springframework.cloud.sleuth.instrument.web.TraceHandlerInterceptor;

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
		if (bean instanceof ZuulHandlerMapping) {
			if (log.isDebugEnabled()) {
				log.debug("Attaching trace interceptor to bean [" + beanName + "] of type [" + bean.getClass().getSimpleName() + "]");
			}
			RouteLocator routeLocator = this.beanFactory.getBean(RouteLocator.class);
			ZuulController zuulController = this.beanFactory.getBean(ZuulController.class);
			ZuulHandlerMappingWrapper wrapper = new ZuulHandlerMappingWrapper(routeLocator, zuulController);
			wrapper.setErrorController(errorController());
			wrapper.setInterceptors(new TraceHandlerInterceptor(this.beanFactory));
			wrapper.forceRefresh();
			return wrapper;
		}
		return bean;
	}

	private ErrorController errorController() {
		try {
			return this.beanFactory.getBean(ErrorController.class);
		} catch (NoSuchBeanDefinitionException e) {
			return null;
		}
	}

	class ZuulHandlerMappingWrapper extends ZuulHandlerMapping {

		public ZuulHandlerMappingWrapper(RouteLocator routeLocator, ZuulController zuul) {
			super(routeLocator, zuul);
		}

		void forceRefresh() {
			initInterceptors();
		}
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}
}
