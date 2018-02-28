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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.openfeign.FeignContext;

/**
 * Post processor that wraps Feign Context in its tracing representations.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.2
 */
final class FeignContextBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	FeignContextBeanPostProcessor(BeanFactory beanFactory) {
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
		if (bean instanceof FeignContext && !(bean instanceof TraceFeignContext)) {
			return new TraceFeignContext(traceFeignObjectWrapper(), (FeignContext) bean);
		}
		return bean;
	}

	private TraceFeignObjectWrapper traceFeignObjectWrapper() {
		return new TraceFeignObjectWrapper(this.beanFactory);
	}
}
