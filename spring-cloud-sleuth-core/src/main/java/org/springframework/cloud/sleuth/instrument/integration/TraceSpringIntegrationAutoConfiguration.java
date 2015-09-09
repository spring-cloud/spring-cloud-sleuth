/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.integration;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.GlobalChannelInterceptor;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnClass(GlobalChannelInterceptor.class)
@ConditionalOnBean(TraceManager.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@ConditionalOnProperty(value = "spring.sleuth.integration.enabled", matchIfMissing = true)
public class TraceSpringIntegrationAutoConfiguration {

	@Bean
	@GlobalChannelInterceptor
	public TraceContextPropagationChannelInterceptor traceContextPropagationChannelInterceptor(
			TraceManager trace) {
		return new TraceContextPropagationChannelInterceptor(trace);
	}

	@Bean
	@GlobalChannelInterceptor
	public TraceChannelInterceptor traceChannelInterceptor(TraceManager trace) {
		return new TraceChannelInterceptor(trace);
	}

}
