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

package org.springframework.cloud.sleuth.log;

import brave.propagation.CurrentTraceContext;
import org.slf4j.MDC;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables a {@link Slf4jCurrentTraceContext} that prints tracing information in the logs.
 * <p>
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnProperty(value="spring.sleuth.enabled", matchIfMissing=true)
@AutoConfigureBefore(TraceAutoConfiguration.class)
public class SleuthLogAutoConfiguration {

	@Configuration
	@ConditionalOnClass(MDC.class)
	@EnableConfigurationProperties(SleuthSlf4jProperties.class)
	protected static class Slf4jConfiguration {

		@Bean
		@ConditionalOnProperty(value = "spring.sleuth.log.slf4j.enabled", matchIfMissing = true)
		@ConditionalOnMissingBean
		public CurrentTraceContext slf4jSpanLogger() {
			return Slf4jCurrentTraceContext.create();
		}

		@Bean
		@ConditionalOnProperty(value = "spring.sleuth.log.slf4j.enabled", matchIfMissing = true)
		@ConditionalOnBean(CurrentTraceContext.class)
		public static BeanPostProcessor slf4jSpanLoggerBPP() {
			return new Slf4jBeanPostProcessor();
		}

		static class Slf4jBeanPostProcessor implements BeanPostProcessor {

			@Override public Object postProcessBeforeInitialization(Object bean,
					String beanName) throws BeansException {
				return bean;
			}

			@Override public Object postProcessAfterInitialization(Object bean,
					String beanName) throws BeansException {
				if (bean instanceof CurrentTraceContext && !(bean instanceof Slf4jCurrentTraceContext)) {
					return Slf4jCurrentTraceContext.create((CurrentTraceContext) bean);
				}
				return bean;
			}
		}
	}
}
