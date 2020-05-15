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

import brave.propagation.CurrentTraceContext;
import org.slf4j.MDC;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.SleuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link Configuration} that adds a {@link Slf4jScopeDecorator} that prints tracing
 * information in the logs.
 * <p>
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 2.0.0
 * @deprecated Do not use this type directly as it was removed in 3.x
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
// This is not auto-configuration, but it was in the past. Leaving the name as
// SleuthLogAutoConfiguration because some may have imported this directly.
// A less precise name is better than rev-locking code.
@Deprecated
public class SleuthLogAutoConfiguration {

	/**
	 * Configuration for Slfj4.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(MDC.class)
	@EnableConfigurationProperties(SleuthSlf4jProperties.class)
	public static class Slf4jConfiguration {

		@Bean
		@ConditionalOnProperty(value = "spring.sleuth.log.slf4j.enabled",
				matchIfMissing = true)
		static CurrentTraceContext.ScopeDecorator slf4jSpanDecorator(
				SleuthProperties sleuthProperties,
				SleuthSlf4jProperties sleuthSlf4jProperties) {
			return new Slf4jScopeDecorator(sleuthProperties, sleuthSlf4jProperties);
		}

	}

}
