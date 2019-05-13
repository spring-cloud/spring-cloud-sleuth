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

package org.springframework.cloud.sleuth.dynamic;

import brave.Tracing;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Role;

/**
 * @author Taras Danylchuk
 * @since 2.2.0
 */
@Configuration
@EnableAspectJAutoProxy
@ConditionalOnProperty(name = "spring.sleuth.dynamic.tracing.expression")
@ConditionalOnBean(Tracing.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@EnableConfigurationProperties(SleuthDynamicAspectJTracingProperties.class)
public class SleuthDynamicAspectJTracingAutoConfiguration {

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public Advisor sleuthDynamicAspectJAdvisor(
			SleuthDynamicAspectJTracingProperties properties,
			SleuthDynamicAspectJTracingInterceptor interceptor) {
		AspectJExpressionPointcut pc = new AspectJExpressionPointcut();
		pc.setExpression(properties.getExpression());
		return new DefaultPointcutAdvisor(pc, interceptor);
	}

	@Bean
	public SleuthDynamicAspectJTracingInterceptor sleuthDynamicAspectJTracingInterceptor(
			SleuthDynamicAspectJTracingProperties properties) {
		return new SleuthDynamicAspectJTracingInterceptor(properties.isTraceParameters());
	}

}
