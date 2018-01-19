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

import brave.http.HttpTracing;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;

/**
 * Post processor that wraps a {@link RibbonCommandFactory}
 * in its trace representation.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.1.0
 */
final class TraceRibbonCommandFactoryBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;
	private HttpTracing tracing;

	TraceRibbonCommandFactoryBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof RibbonCommandFactory) {
			return new TraceRibbonCommandFactory((RibbonCommandFactory) bean, tracing());
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	HttpTracing tracing() {
		if (this.tracing == null) {
			this.tracing = this.beanFactory.getBean(HttpTracing.class);
		}
		return this.tracing;
	}
}
