/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.brave.instrument.redis;

import brave.Tracing;
import brave.sampler.Sampler;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.tracing.BraveTracing;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.autoconfig.instrument.redis.TraceRedisAutoConfiguration;
import org.springframework.cloud.sleuth.brave.instrument.redis.ClientResourcesBuilderCustomizer;
import org.springframework.cloud.sleuth.brave.instrument.redis.TraceLettuceClientResourcesBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables Redis span information propagation.
 *
 * @author Chao Chang
 * @since 2.2.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.redis.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracing.class)
@AutoConfigureAfter(BraveAutoConfiguration.class)
@AutoConfigureBefore({ RedisAutoConfiguration.class, TraceRedisAutoConfiguration.class })
@EnableConfigurationProperties(TraceRedisProperties.class)
@ConditionalOnClass(BraveTracing.class)
public class BraveRedisAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty("spring.sleuth.redis.legacy.enabled")
	static class LegacyBraveOnlyLettuceConfiguration {

		// TODO: The customization auto configuration should come from Spring Boot
		@Bean(destroyMethod = "shutdown")
		@ConditionalOnMissingBean({ ClientResources.class,
				org.springframework.cloud.sleuth.instrument.redis.TraceLettuceClientResourcesBuilderCustomizer.class })
		DefaultClientResources traceLettuceClientResources(
				ObjectProvider<ClientResourcesBuilderCustomizer> customizer) {
			DefaultClientResources.Builder builder = DefaultClientResources.builder();
			customizer.stream().forEach(c -> c.customize(builder));
			return builder.build();
		}

		@Bean
		@ConditionalOnMissingBean(org.springframework.cloud.sleuth.instrument.redis.TraceLettuceClientResourcesBuilderCustomizer.class)
		TraceLettuceClientResourcesBuilderCustomizer traceBraveOnlyLettuceClientResourcesBuilderCustomizer(
				Tracing tracing, TraceRedisProperties traceRedisProperties, Sampler sampler) {
			eagerlyInitializePotentiallyRefreshScopeSampler(sampler);
			return new TraceLettuceClientResourcesBuilderCustomizer(tracing,
					traceRedisProperties.getRemoteServiceName());
		}

		/**
		 * We need to do the eager method invocation. Since this might be @RefreshScope, a
		 * proxy is being created. Trying to resolve the proxy from a different thread
		 * than main can lead to cross thread locking.
		 * @param sampler potentially refresh scope sampler
		 */
		private void eagerlyInitializePotentiallyRefreshScopeSampler(Sampler sampler) {
			sampler.isSampled(0L);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(value = "spring.sleuth.redis.legacy.enabled", havingValue = "false", matchIfMissing = true)
	static class NewBraveLettuceConfiguration {

		@Bean
		BraveTracing lettuceBraveTracing(Tracing tracing, TraceRedisProperties traceRedisProperties) {
			return BraveTracing.builder().tracing(tracing).excludeCommandArgsFromSpanTags()
					.serviceName(traceRedisProperties.getRemoteServiceName()).build();
		}

	}

}
