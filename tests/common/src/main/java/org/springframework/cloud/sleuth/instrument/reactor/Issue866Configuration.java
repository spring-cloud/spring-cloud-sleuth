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

package org.springframework.cloud.sleuth.instrument.reactor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Marcin Grzejszczak
 */
@Configuration(proxyBeanMethods = false)
public class Issue866Configuration {

	private static final Log log = LogFactory.getLog(Issue866Configuration.class);

	/**
	 * We don't want to force direct dependencies between components because Spring might
	 * just properly setup the context we want to ensure that the HRBDRPP is always
	 * executed before any other object is started.
	 */
	public static TestHook hook;

	@Bean
	HookRegisteringBeanDefinitionRegistryPostProcessor overridingProcessorForTests(
			ConfigurableApplicationContext context) {
		log.info("Registering a HookRegisteringBeanDefinitionRegistryPostProcessor for context [" + context + "]");
		TestHook hook = new TestHook(context);
		Issue866Configuration.hook = hook;
		return hook;
	}

	/**
	 * Test Hook.
	 */
	public static class TestHook extends HookRegisteringBeanDefinitionRegistryPostProcessor {

		/**
		 * Whether the hook was called.
		 */
		public boolean executed = false;

		public TestHook(ConfigurableApplicationContext context) {
			super(context);
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
			super.postProcessBeanFactory(beanFactory);
			this.executed = true;
		}

	}

}
