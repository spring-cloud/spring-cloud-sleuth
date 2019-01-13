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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;

/**
 * Utility class that verifies that context is in creation. TODO: Consider getting this
 * info from Spring Framework
 *
 * @author Marcin Grzejszczak
 * @since 2.1.0
 */
class ContextUtil {

	private static final Log log = LogFactory.getLog(ContextUtil.class);

	private static Map<BeanFactory, ContextRefreshedListener> CACHE = new ConcurrentHashMap<>();

	static boolean isContextInCreation(BeanFactory beanFactory) {
		ContextRefreshedListener bean = CACHE.compute(beanFactory,
				(beanFactory1, contextRefreshedListener) -> {
					if (contextRefreshedListener != null
							&& contextRefreshedListener.get()) {
						return contextRefreshedListener;
					}
					// TODO: There has to be a better way than this
					return ContextRefreshedListener.instance();
				});
		boolean contextRefreshed = bean.get();
		if (!contextRefreshed && log.isDebugEnabled()) {
			log.debug("Context is not ready yet");
		}
		return !contextRefreshed;
	}

}