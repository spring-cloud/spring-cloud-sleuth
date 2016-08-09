/*
 * Copyright 2013-2016 the original author or authors.
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

import java.io.IOException;
import java.lang.reflect.Type;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.cloud.netflix.feign.FeignAutoConfiguration;
import org.springframework.cloud.netflix.feign.support.ResponseEntityDecoder;
import org.springframework.cloud.netflix.feign.support.SpringDecoder;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceHeaders;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.hystrix.SleuthHystrixAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

import feign.Client;
import feign.Feign;
import feign.FeignException;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.Decoder;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables span information propagation when using Feign.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.feign.enabled", matchIfMissing = true)
@ConditionalOnClass(Client.class)
@ConditionalOnBean(Tracer.class)
@AutoConfigureBefore(FeignAutoConfiguration.class)
@AutoConfigureAfter(SleuthHystrixAutoConfiguration.class)
public class TraceFeignClientAutoConfiguration {

	@Autowired
	private ObjectFactory<HttpMessageConverters> messageConverters;

	@Bean
	@Scope("prototype")
	@ConditionalOnClass(name = "com.netflix.hystrix.HystrixCommand")
	@ConditionalOnProperty(name = "feign.hystrix.enabled", matchIfMissing = true)
	Feign.Builder feignHystrixBuilder(BeanFactory beanFactory) {
		return SleuthHystrixFeignBuilder.builder(beanFactory);
	}

	@Bean
	@ConditionalOnMissingBean
	@Scope("prototype")
	@ConditionalOnProperty(name = "feign.hystrix.enabled", havingValue = "false", matchIfMissing = false)
	Feign.Builder feignBuilder(BeanFactory beanFactory) {
		return SleuthFeignBuilder.builder(beanFactory);
	}

	@Configuration
	@ConditionalOnProperty(name = "spring.sleuth.feign.processor.enabled", matchIfMissing = true)
	protected static class FeignBeanPostProcessorConfiguration {

		@Bean
		FeignBeanPostProcessor feignBeanPostProcessor(TraceFeignObjectWrapper traceFeignObjectWrapper) {
			return new FeignBeanPostProcessor(traceFeignObjectWrapper);
		}

		@Bean
		FeignContextBeanPostProcessor feignContextBeanPostProcessor(BeanFactory beanFactory) {
			return new FeignContextBeanPostProcessor(beanFactory);
		}
	}

	@Bean
	TraceFeignObjectWrapper traceFeignObjectWrapper(BeanFactory beanFactory) {
		return new TraceFeignObjectWrapper(beanFactory);
	}

	@Bean
	@Primary
	Decoder feignDecoder(BeanFactory beanFactory, final TraceHeaders traceHeaders) {
		return new TraceFeignDecoder(beanFactory,
				new ResponseEntityDecoder(new SpringDecoder(this.messageConverters)) {
					@Override
					public Object decode(Response response, Type type)
							throws IOException, FeignException {
						FeignRequestContext feignRequestContext = FeignRequestContext
								.getInstance();
						FeignResponseHeadersHolder feignResponseHeadersHolder = new FeignResponseHeadersHolder(
								response.headers());
						feignResponseHeadersInjector(traceHeaders).inject(
								feignRequestContext.getCurrentSpan(),
								feignResponseHeadersHolder);
						return super.decode(
								Response.create(response.status(), response.reason(),
										feignResponseHeadersHolder.responseHeaders,
										response.body()),
								type);
					}
				});
	}

	/**
	 * Sleuth {@link feign.RequestInterceptor} that either starts a new Span or continues
	 * an existing one if a retry takes place.
	 */
	@Bean
	RequestInterceptor traceIdRequestInterceptor(Tracer tracer, TraceHeaders traceHeaders) {
		return new TraceFeignRequestInterceptor(tracer, feignRequestTemplateInjector(traceHeaders));
	}

	private SpanInjector<RequestTemplate> feignRequestTemplateInjector(TraceHeaders traceHeaders) {
		return new FeignRequestTemplateInjector(traceHeaders);
	}

	private SpanInjector<FeignResponseHeadersHolder> feignResponseHeadersInjector(TraceHeaders traceHeaders) {
		return new FeignResponseHeadersInjector(traceHeaders);
	}
}
