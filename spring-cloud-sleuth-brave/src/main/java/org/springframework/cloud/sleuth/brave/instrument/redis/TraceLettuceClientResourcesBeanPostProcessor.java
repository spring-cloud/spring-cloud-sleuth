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

package org.springframework.cloud.sleuth.brave.instrument.redis;

import brave.Tracing;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.tracing.BraveTracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * {@link BeanPostProcessor} for wrapping Lettuce components in a tracing representation.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class TraceLettuceClientResourcesBeanPostProcessor implements BeanPostProcessor {

	private static final Log log = LogFactory.getLog(TraceLettuceClientResourcesBeanPostProcessor.class);

	private final BeanFactory beanFactory;

	private final TraceRedisProperties traceRedisProperties;

	private Tracing tracing;

	public TraceLettuceClientResourcesBeanPostProcessor(BeanFactory beanFactory,
			TraceRedisProperties traceRedisProperties) {
		this.beanFactory = beanFactory;
		this.traceRedisProperties = traceRedisProperties;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof ClientResources) {
			ClientResources cr = (ClientResources) bean;
			if (!cr.tracing().isEnabled()) {
				if (log.isDebugEnabled()) {
					log.debug("Lettuce ClientResources bean is auto-configured to enable tracing.");
				}
				BraveTracing lettuceTracing = BraveTracing.builder().tracing(tracing()).excludeCommandArgsFromSpanTags()
						.serviceName(traceRedisProperties.getRemoteServiceName()).build();
				return cr.mutate().tracing(lettuceTracing).build();
			}
			if (log.isDebugEnabled()) {
				log.debug(
						"Lettuce ClientResources bean is skipped for auto-configuration because tracing was already enabled.");
			}
		}
		return bean;
	}

	private Tracing tracing() {
		if (this.tracing == null) {
			this.tracing = this.beanFactory.getBean(Tracing.class);
		}
		return this.tracing;
	}

}
