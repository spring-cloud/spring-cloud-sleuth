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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import brave.http.HttpTracing;
import feign.Client;
import feign.Feign;
import feign.okhttp.OkHttpClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignContext;
import org.springframework.cloud.sleuth.instrument.hystrix.SleuthHystrixAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.TraceHttpAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables span information propagation when using Feign.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.feign.enabled", matchIfMissing = true)
@ConditionalOnClass({ Client.class, FeignContext.class })
@ConditionalOnBean(HttpTracing.class)
@AutoConfigureBefore(FeignAutoConfiguration.class)
@AutoConfigureAfter({SleuthHystrixAutoConfiguration.class, TraceHttpAutoConfiguration.class})
public class TraceFeignClientAutoConfiguration {

	@Bean
	@Scope("prototype")
	@ConditionalOnClass(name = {"com.netflix.hystrix.HystrixCommand", "feign.hystrix.HystrixFeign"})
	@ConditionalOnProperty(name = "feign.hystrix.enabled", havingValue = "true")
	Feign.Builder feignHystrixBuilder(BeanFactory beanFactory) {
		return SleuthHystrixFeignBuilder.builder(beanFactory);
	}

	@Bean
	@ConditionalOnMissingBean
	@Scope("prototype")
	@ConditionalOnProperty(name = "feign.hystrix.enabled", havingValue = "false", matchIfMissing = true)
	Feign.Builder feignBuilder(BeanFactory beanFactory) {
		return SleuthFeignBuilder.builder(beanFactory);
	}

	@Configuration
	@ConditionalOnProperty(name = "spring.sleuth.feign.processor.enabled", matchIfMissing = true)
	protected static class FeignBeanPostProcessorConfiguration {

		@Bean static FeignContextBeanPostProcessor feignContextBeanPostProcessor(BeanFactory beanFactory) {
			return new FeignContextBeanPostProcessor(beanFactory);
		}
	}

	@Configuration
	@ConditionalOnClass(OkHttpClient.class)
	protected static class OkHttpClientFeignBeanPostProcessorConfiguration {

		@Bean static OkHttpFeignClientBeanPostProcessor okHttpFeignClientBeanPostProcessor(BeanFactory beanFactory) {
			return new OkHttpFeignClientBeanPostProcessor(beanFactory);
		}
	}

	@Bean
	TraceFeignObjectWrapper traceFeignObjectWrapper(BeanFactory beanFactory) {
		return new TraceFeignObjectWrapper(beanFactory);
	}

	@Bean TraceFeignAspect traceFeignAspect(BeanFactory beanFactory) {
		return new TraceFeignAspect(beanFactory);
	}
}
