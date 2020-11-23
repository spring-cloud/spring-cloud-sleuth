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

package org.springframework.cloud.sleuth.autoconfig.instrument.web.client;

import reactor.netty.http.client.HttpClient;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.cloud.commons.httpclient.HttpClientConfiguration;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.autoconfig.otel.OtelAutoConfiguration;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.instrument.web.client.HttpClientBeanPostProcessor;
import org.springframework.cloud.sleuth.instrument.web.client.LazyTraceClientHttpRequestInterceptor;
import org.springframework.cloud.sleuth.instrument.web.client.TraceRequestHttpHeadersFilter;
import org.springframework.cloud.sleuth.instrument.web.client.TraceResponseHttpHeadersFilter;
import org.springframework.cloud.sleuth.instrument.web.client.TraceRestTemplateBeanPostProcessor;
import org.springframework.cloud.sleuth.instrument.web.client.TraceRestTemplateCustomizer;
import org.springframework.cloud.sleuth.instrument.web.client.TraceUserInfoRestTemplateCustomizer;
import org.springframework.cloud.sleuth.instrument.web.client.TraceWebClientBeanPostProcessor;
import org.springframework.cloud.sleuth.instrument.web.client.UserInfoRestTemplateCustomizerBeanPostProcessor;
import org.springframework.cloud.sleuth.instrument.web.mvc.TracingClientHttpRequestInterceptor;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables span information propagation when using
 * {@link RestTemplate}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalnOnSleuthWebClient
@ConditionalOnBean(Tracer.class)
@AutoConfigureBefore(HttpClientConfiguration.class)
@AutoConfigureAfter({ BraveAutoConfiguration.class, OtelAutoConfiguration.class })
class TraceWebClientAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(RestTemplate.class)
	static class RestTemplateConfig {

		@Bean
		public TracingClientHttpRequestInterceptor tracingClientHttpRequestInterceptor(
				CurrentTraceContext currentTraceContext, HttpClientHandler httpClientHandler) {
			return (TracingClientHttpRequestInterceptor) TracingClientHttpRequestInterceptor.create(currentTraceContext,
					httpClientHandler);
		}

		@Configuration(proxyBeanMethods = false)
		protected static class TraceInterceptorConfiguration {

			@Autowired
			private BeanFactory beanFactory;

			@Bean
			static TraceRestTemplateBeanPostProcessor traceRestTemplateBeanPostProcessor(
					ListableBeanFactory beanFactory) {
				return new TraceRestTemplateBeanPostProcessor(beanFactory);
			}

			@Bean
			@Order
			RestTemplateCustomizer traceRestTemplateCustomizer() {
				return new TraceRestTemplateCustomizer(new LazyTraceClientHttpRequestInterceptor(this.beanFactory));
			}

		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HttpHeadersFilter.class)
	static class HttpHeadersFilterConfig {

		@Bean
		HttpHeadersFilter traceRequestHttpHeadersFilter(Tracer tracer, HttpClientHandler handler,
				Propagator propagator) {
			return new TraceRequestHttpHeadersFilter(tracer, handler, propagator);
		}

		@Bean
		HttpHeadersFilter traceResponseHttpHeadersFilter(Tracer tracer, HttpClientHandler handler,
				Propagator propagator) {
			return new TraceResponseHttpHeadersFilter(tracer, handler, propagator);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HttpClient.class)
	static class NettyConfiguration {

		@Bean
		static HttpClientBeanPostProcessor httpClientBeanPostProcessor(ConfigurableApplicationContext springContext) {
			return new HttpClientBeanPostProcessor(springContext);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(WebClient.class)
	@ConditionalOnProperty(value = "spring.sleuth.web.webclient.enabled", matchIfMissing = true)
	static class WebClientConfig {

		@Bean
		static TraceWebClientBeanPostProcessor traceWebClientBeanPostProcessor(
				ConfigurableApplicationContext springContext) {
			return new TraceWebClientBeanPostProcessor(springContext);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ UserInfoRestTemplateCustomizer.class, OAuth2RestTemplate.class })
	protected static class TraceOAuthConfiguration {

		@Bean
		static UserInfoRestTemplateCustomizerBeanPostProcessor userInfoRestTemplateCustomizerBeanPostProcessor(
				BeanFactory beanFactory) {
			return new UserInfoRestTemplateCustomizerBeanPostProcessor(beanFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		static UserInfoRestTemplateCustomizer traceUserInfoRestTemplateCustomizer(BeanFactory beanFactory) {
			return new TraceUserInfoRestTemplateCustomizer(beanFactory);
		}

	}

}
