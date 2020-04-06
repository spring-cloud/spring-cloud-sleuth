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

package org.springframework.cloud.sleuth.log;

import java.util.List;

import brave.baggage.CorrelationScopeConfig;
import brave.baggage.CorrelationScopeDecorator;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import org.slf4j.MDC;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.PropertyBasedBaggageConfiguration;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} adds a {@link CorrelationScopeDecorator} that prints tracing
 * information in the logs.
 * <p>
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
@AutoConfigureBefore(TraceAutoConfiguration.class)
@AutoConfigureAfter(PropertyBasedBaggageConfiguration.class)
public class SleuthLogAutoConfiguration {

	/**
	 * Configuration for Slfj4.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(MDC.class)
	@EnableConfigurationProperties(SleuthSlf4jProperties.class)
	protected static class Slf4jConfiguration {

		@Bean
		@ConditionalOnProperty(value = "spring.sleuth.log.slf4j.enabled",
				matchIfMissing = true)
		ScopeDecorator correlationScopeDecorator(
				List<CorrelationScopeConfig> correlationScopeConfigs) {
			CorrelationScopeDecorator.Builder builder = MDCScopeDecorator.newBuilder();
			builder.clear();
			correlationScopeConfigs.forEach(builder::add);
			return builder.build();
		}

	}

}
