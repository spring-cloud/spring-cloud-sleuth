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

package org.springframework.cloud.sleuth.autoconfig.instrument.async;

import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.instrument.async.ExecutorInstrumentor;

/**
 * Bean post processor that wraps a call to an {@link Executor} either in a JDK or CGLIB
 * proxy. Depending on whether the implementation has a final method or is final.
 *
 * @author Marcin Grzejszczak
 * @author Jesus Alonso
 * @author Denys Ivano
 * @author Vladislav Fefelov
 * @since 1.1.4
 */
public class ExecutorBeanPostProcessor implements BeanPostProcessor {

	private static final Log log = LogFactory.getLog(ExecutorBeanPostProcessor.class);

	private final BeanFactory beanFactory;

	private SleuthAsyncProperties sleuthAsyncProperties;

	public ExecutorBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (!ExecutorInstrumentor.isApplicableForInstrumentation(bean)) {
			return bean;
		}
		return new ExecutorInstrumentor(() -> sleuthAsyncProperties().getIgnoredBeans(), this.beanFactory)
				.instrument(bean, beanName);
	}

	private SleuthAsyncProperties sleuthAsyncProperties() {
		if (this.sleuthAsyncProperties == null) {
			this.sleuthAsyncProperties = this.beanFactory.getBean(SleuthAsyncProperties.class);
		}
		return this.sleuthAsyncProperties;
	}

}
