/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.instrument.messaging;

import brave.spring.rabbit.SpringRabbitTracing;

import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Instruments spring-rabbit related components.
 */
public class SleuthRabbitBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	private SpringRabbitTracing tracing;

	public SleuthRabbitBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof RabbitTemplate) {
			return rabbitTracing().decorateRabbitTemplate((RabbitTemplate) bean);
		}
		else if (bean instanceof SimpleRabbitListenerContainerFactory) {
			return rabbitTracing()
					.decorateSimpleRabbitListenerContainerFactory((SimpleRabbitListenerContainerFactory) bean);
		}
		else if (bean instanceof DirectRabbitListenerContainerFactory) {
			return rabbitTracing()
					.decorateDirectRabbitListenerContainerFactory((DirectRabbitListenerContainerFactory) bean);
		}
		else if (bean instanceof SimpleMessageListenerContainer || bean instanceof DirectMessageListenerContainer) {
			return rabbitTracing()
					.decorateMessageListenerContainer((AbstractMessageListenerContainer) bean);
		}
		return bean;
	}

	SpringRabbitTracing rabbitTracing() {
		if (this.tracing == null) {
			this.tracing = this.beanFactory.getBean(SpringRabbitTracing.class);
		}
		return this.tracing;
	}

}
