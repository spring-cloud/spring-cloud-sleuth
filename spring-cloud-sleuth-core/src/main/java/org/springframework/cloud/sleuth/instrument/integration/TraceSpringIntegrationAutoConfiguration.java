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
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.GlobalChannelInterceptor;

import java.util.Random;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnClass(GlobalChannelInterceptor.class)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@ConditionalOnProperty(value = "spring.sleuth.integration.enabled", matchIfMissing = true)
public class TraceSpringIntegrationAutoConfiguration {

	@Bean
	@GlobalChannelInterceptor
	public TraceContextPropagationChannelInterceptor traceContextPropagationChannelInterceptor(
			Tracer tracer) {
		return new TraceContextPropagationChannelInterceptor(tracer);
	}

	@Bean
	@GlobalChannelInterceptor
	public TraceChannelInterceptor traceChannelInterceptor(Tracer tracer, Random random) {
		return new TraceChannelInterceptor(tracer, random);
	}

	@Bean
	public TraceStompMessageChannelInterceptor traceStompMessageChannelInterceptor(Tracer tracer, Random random) {
		return new TraceStompMessageChannelInterceptor(tracer, random);
	}

	@Bean
	public TraceStompMessageContextPropagationChannelInterceptor traceStompMessageContextPropagationChannelInteceptor(
			Tracer tracer) {
		return new TraceStompMessageContextPropagationChannelInterceptor(tracer);
	}

}
