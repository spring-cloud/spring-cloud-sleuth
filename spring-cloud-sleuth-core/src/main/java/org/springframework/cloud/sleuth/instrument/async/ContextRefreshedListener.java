/*
 * Copyright 2013-2019 the original author or authors.
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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;

class ContextRefreshedListener extends AtomicBoolean implements SmartApplicationListener {

	static final Map<BeanFactory, ContextRefreshedListener> CACHE = new ConcurrentHashMap<>();

	private static final Log log = LogFactory.getLog(ContextRefreshedListener.class);

	ContextRefreshedListener(boolean initialValue) {
		super(initialValue);
	}

	ContextRefreshedListener() {
		this(false);
	}

	static ContextRefreshedListener getBean(BeanFactory beanFactory) {
		return CACHE.getOrDefault(beanFactory, new ContextRefreshedListener(false));
	}

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ContextRefreshedEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ContextRefreshedEvent) {
			if (log.isDebugEnabled()) {
				log.debug("Context successfully refreshed");
			}
			ContextRefreshedEvent contextRefreshedEvent = (ContextRefreshedEvent) event;
			ApplicationContext context = contextRefreshedEvent.getApplicationContext();
			BeanFactory beanFactory = context;
			if (context instanceof ConfigurableApplicationContext) {
				beanFactory = ((ConfigurableApplicationContext) context).getBeanFactory();
			}
			ContextRefreshedListener listener = CACHE.getOrDefault(beanFactory, this);
			listener.set(true);
			CACHE.put(beanFactory, listener);
		}
	}

}
