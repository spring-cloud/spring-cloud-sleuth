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

package org.springframework.cloud.sleuth.instrument.async;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that wraps an existing custom {@link AsyncConfigurer} in a
 * {@link LazyTraceAsyncCustomizer}.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(AsyncConfigurer.class)
@ConditionalOnBean(AsyncConfigurer.class)
@AutoConfigureBefore(AsyncDefaultAutoConfiguration.class)
@ConditionalOnProperty(value = "spring.sleuth.async.enabled", matchIfMissing = true)
@AutoConfigureAfter(name = "org.springframework.cloud.sleuth.instrument.scheduling.TraceSchedulingAutoConfiguration")
class AsyncCustomAutoConfiguration implements BeanPostProcessor {

	@Autowired
	private BeanFactory beanFactory;

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof AsyncConfigurer && !(bean instanceof LazyTraceAsyncCustomizer)) {
			AsyncConfigurer configurer = (AsyncConfigurer) bean;
			return new LazyTraceAsyncCustomizer(this.beanFactory, configurer);
		}
		return bean;
	}

}
