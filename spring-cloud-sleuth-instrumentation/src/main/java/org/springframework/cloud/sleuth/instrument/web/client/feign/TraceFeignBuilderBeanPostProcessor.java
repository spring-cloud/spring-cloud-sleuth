/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.lang.reflect.Field;

import feign.Client;
import feign.Feign;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

/**
 * {@link BeanPostProcessor} that ensures that each {@link Feign.Builder} has a trace
 * representation of a {@link Client}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.2
 */
public class TraceFeignBuilderBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	public TraceFeignBuilderBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof Feign.Builder) {
			Field client = ReflectionUtils.findField(Feign.Builder.class, "client");
			ReflectionUtils.makeAccessible(client);
			Feign.Builder delegate = (Feign.Builder) bean;
			Client clientInDelegate = (Client) ReflectionUtils.getField(client, delegate);
			if (clientInDelegate instanceof LazyClient || clientInDelegate instanceof LazyTracingFeignClient) {
				return bean;
			}
			delegate.client(new LazyClient(this.beanFactory, clientInDelegate));
			return bean;
		}
		return bean;
	}

}
