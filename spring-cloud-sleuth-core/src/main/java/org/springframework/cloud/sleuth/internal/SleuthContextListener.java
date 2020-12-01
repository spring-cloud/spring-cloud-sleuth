/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;

/**
 * Internal tool used by Sleuth. Do not use.
 *
 * @author Marcin Grzejszczak
 * @since 2.2.5
 */
public class SleuthContextListener implements SmartApplicationListener {

	static final Map<BeanFactory, SleuthContextListener> CACHE = new ConcurrentHashMap<>();

	private static final Log log = LogFactory.getLog(SleuthContextListener.class);

	final AtomicBoolean refreshed;

	final AtomicBoolean closed;

	public SleuthContextListener() {
		this.refreshed = new AtomicBoolean();
		this.closed = new AtomicBoolean();
	}

	SleuthContextListener(AtomicBoolean refreshed, AtomicBoolean closed) {
		this.refreshed = refreshed;
		this.closed = closed;
	}

	/**
	 * Returns an instance of the {@link SleuthContextListener} that might have already
	 * been initialized.
	 * @param beanFactory bean factory
	 * @return instance of {@link SleuthContextListener}
	 */
	public static SleuthContextListener getBean(BeanFactory beanFactory) {
		return CACHE.getOrDefault(beanFactory, new SleuthContextListener());
	}

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ContextClosedEvent.class.isAssignableFrom(eventType)
				|| ContextRefreshedEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ContextRefreshedEvent
				|| event instanceof ContextClosedEvent) {
			if (log.isDebugEnabled()) {
				log.debug("Context refreshed or closed [" + event + "]");
			}
			ApplicationContextEvent contextEvent = (ApplicationContextEvent) event;
			ApplicationContext context = contextEvent.getApplicationContext();
			BeanFactory beanFactory = context;
			if (context instanceof ConfigurableApplicationContext) {
				beanFactory = ((ConfigurableApplicationContext) context).getBeanFactory();
			}
			SleuthContextListener listener = CACHE.getOrDefault(beanFactory, this);
			listener.refreshed.compareAndSet(false,
					event instanceof ContextRefreshedEvent);
			listener.closed.compareAndSet(false, event instanceof ContextClosedEvent);
			CACHE.put(beanFactory, listener);
		}
	}

	/**
	 * @return @{code true} when Spring Context has NOT yet been started
	 */
	public boolean isUnusable() {
		return !this.refreshed.get() || this.closed.get();
	}

}
