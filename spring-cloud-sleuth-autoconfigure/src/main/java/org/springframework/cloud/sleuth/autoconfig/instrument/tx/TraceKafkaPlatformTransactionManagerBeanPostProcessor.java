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

package org.springframework.cloud.sleuth.autoconfig.instrument.tx;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.instrument.tx.TraceKafkaAwareTransactionManager;
import org.springframework.kafka.transaction.KafkaAwareTransactionManager;

/**
 * Post processor that wraps a {@link KafkaAwareTransactionManager}.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 * @deprecated scheduled for removal
 */
@Deprecated
public class TraceKafkaPlatformTransactionManagerBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	private final TracePlatformTransactionManagerBeanPostProcessor delegate;

	public TraceKafkaPlatformTransactionManagerBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.delegate = new TracePlatformTransactionManagerBeanPostProcessor(beanFactory);
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof KafkaAwareTransactionManager && !(bean instanceof TraceKafkaAwareTransactionManager)) {
			return new TraceKafkaAwareTransactionManager((KafkaAwareTransactionManager) bean, this.beanFactory);
		}
		return this.delegate.postProcessAfterInitialization(bean, beanName);
	}

}
