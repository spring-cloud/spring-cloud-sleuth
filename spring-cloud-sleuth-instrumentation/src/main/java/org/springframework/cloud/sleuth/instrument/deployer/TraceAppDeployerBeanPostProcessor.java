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

package org.springframework.cloud.sleuth.instrument.deployer;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.core.env.Environment;

/**
 * {@link BeanPostProcessor} to wrap a {@link AppDeployer} instance into its trace
 * representation.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public class TraceAppDeployerBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	private final Environment environment;

	public TraceAppDeployerBeanPostProcessor(BeanFactory beanFactory, Environment environment) {
		this.beanFactory = beanFactory;
		this.environment = environment;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof AppDeployer && !(bean instanceof TraceAppDeployer)) {
			return new TraceAppDeployer((AppDeployer) bean, this.beanFactory, this.environment);
		}
		return bean;
	}

}
