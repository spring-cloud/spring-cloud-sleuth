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
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
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
	private RouteLocator routeLocator;
	private ZuulController zuul;
	private ErrorController errorController;

	public TraceZuulHandlerMappingBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof ZuulHandlerMapping && !(bean instanceof TraceZuulHandlerMapping)) {
			if (log.isDebugEnabled()) {
				log.debug("Wrapping bean [" + beanName + "] of type [" + bean.getClass().getSimpleName() +
						"] in its trace representation");
			}
			return new TraceZuulHandlerMapping(this.beanFactory, routeLocator(), zuulController(),
					errorController());
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	private static class TraceZuulHandlerMapping extends ZuulHandlerMapping {

		private final BeanFactory beanFactory;

		public TraceZuulHandlerMapping(BeanFactory beanFactory, RouteLocator routeLocator,
				ZuulController zuulController, ErrorController errorController) {
			super(routeLocator, zuulController);
			this.beanFactory = beanFactory;
			setErrorController(errorController);
		}

		@Override
		protected void extendInterceptors(List<Object> interceptors) {
			interceptors.add(new TraceHandlerInterceptor(this.beanFactory));
		}
	}

	private RouteLocator routeLocator() {
		if (this.routeLocator == null) {
			this.routeLocator = this.beanFactory.getBean(RouteLocator.class);
		}
		return this.routeLocator;
	}

	private ZuulController zuulController() {
		if (this.zuul == null) {
			this.zuul = this.beanFactory.getBean(ZuulController.class);
		}
		return this.zuul;
	}

	private ErrorController errorController() {
		if (this.errorController == null) {
			try {
				this.errorController = this.beanFactory.getBean(ErrorController.class);
			} catch (BeansException b) {
				return null;
			}
		}
		return this.errorController;
	}
}
