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

package org.springframework.cloud.sleuth.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.Nullable;

/**
 * Avoids calling the expensive {@link ConfigurableApplicationContext#getBean(Class)} many
 * times or throwing an exception.
 *
 * <p>
 * Note: This is an internal class to sleuth and must not be used by external code.
 */
public final class LazyBean<T> {

	public static <T> LazyBean<T> create(ConfigurableApplicationContext springContext, Class<T> requiredType) {
		return new LazyBean<>(springContext, requiredType);
	}

	// spring-jcl uses commons-logging, so do we.
	private static final Log log = LogFactory.getLog(LazyBean.class);

	final ConfigurableApplicationContext springContext;

	final Class<T> requiredType;

	T value;

	LazyBean(ConfigurableApplicationContext springContext, Class<T> requiredType) {
		this.springContext = springContext;
		this.requiredType = requiredType;
	}

	/**
	 * Attempts to provision from the underlying bean factory, if not already provisioned.
	 * @return the bean value or null if there was an exception getting it.
	 */
	@Nullable
	public T get() {
		try {
			return getOrError();
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("Spring context [" + springContext + "] error getting [" + requiredType + "].", ex);
			}
		}
		return this.value;
	}

	/**
	 * Attempts to provision from the underlying bean factory, if not already provisioned.
	 * @return the bean value. This variant does not catch exception.
	 */
	public T getOrError() {
		T bean = this.value;
		if (bean != null) {
			return bean;
		}

		bean = springContext.getBean(requiredType);
		this.value = bean;
		return bean;
	}

}
