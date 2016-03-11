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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.Tracer;

import feign.Client;
import feign.Retryer;
import feign.codec.Decoder;

/**
 * Post processor that wraps Feign related classes {@link Decoder},
 * {@link Retryer}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class FeignBeanPostProcessor implements BeanPostProcessor {

	private final Tracer tracer;

	FeignBeanPostProcessor(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof Decoder && !(bean instanceof TraceFeignDecoder)) {
			return new TraceFeignDecoder(this.tracer, (Decoder) bean);
		} else if (bean instanceof Retryer && !(bean instanceof TraceFeignRetryer)) {
			return new TraceFeignRetryer(this.tracer, (Retryer) bean);
		} else if (bean instanceof Client && !(bean instanceof TraceFeignClient)) {
			return new TraceFeignClient(this.tracer, (Client) bean);
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}
}
