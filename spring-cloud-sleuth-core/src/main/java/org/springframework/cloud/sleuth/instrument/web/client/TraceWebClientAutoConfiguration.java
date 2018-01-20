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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.PostConstruct;

import brave.http.HttpTracing;
import brave.spring.web.TracingClientHttpRequestInterceptor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables span information propagation when using
 * {@link RestTemplate}
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration
@SleuthWebClientEnabled
@ConditionalOnBean(HttpTracing.class)
@AutoConfigureAfter(TraceWebServletAutoConfiguration.class)
public class TraceWebClientAutoConfiguration {

	@ConditionalOnClass(RestTemplate.class)
	static class RestTemplateConfig {

		@Bean
		public TracingClientHttpRequestInterceptor tracingClientHttpRequestInterceptor(HttpTracing httpTracing) {
			return (TracingClientHttpRequestInterceptor) TracingClientHttpRequestInterceptor.create(httpTracing);
		}

		@Configuration
		protected static class TraceInterceptorConfiguration {

			@Autowired(required = false)
			private Collection<RestTemplate> restTemplates;

			@Autowired
			private TracingClientHttpRequestInterceptor clientInterceptor;

			@PostConstruct
			public void init() {
				if (this.restTemplates != null) {
					for (RestTemplate restTemplate : this.restTemplates) {
						new RestTemplateInterceptorInjector(this.clientInterceptor)
								.inject(restTemplate);
					}
				}
			}
		}

		@Autowired(required = false)
		private Collection<RestTemplate> restTemplates;

		@Autowired
		private TracingClientHttpRequestInterceptor traceRestTemplateInterceptor;

		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE)
		RestTemplateCustomizer traceRestTemplateCustomizer() {
			final TracingClientHttpRequestInterceptor interceptor = this.traceRestTemplateInterceptor;
			return restTemplate ->
					new RestTemplateInterceptorInjector(interceptor).inject(restTemplate);
		}

		@PostConstruct
		public void init() {
			if (this.restTemplates != null) {
				for (RestTemplate restTemplate : this.restTemplates) {
					new RestTemplateInterceptorInjector(
							this.traceRestTemplateInterceptor).inject(restTemplate);
				}
			}
		}
	}

	@ConditionalOnClass(WebClient.class)
	static class WebClientConfig {

		@Bean
		TraceWebClientBeanPostProcessor traceWebClientBeanPostProcessor(BeanFactory beanFactory) {
			return new TraceWebClientBeanPostProcessor(beanFactory);
		}
	}
}

class RestTemplateInterceptorInjector {
	private final TracingClientHttpRequestInterceptor interceptor;

	RestTemplateInterceptorInjector(TracingClientHttpRequestInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	void inject(RestTemplate restTemplate) {
		if (hasTraceInterceptor(restTemplate)) {
			return;
		}
		List<ClientHttpRequestInterceptor> interceptors = new ArrayList<ClientHttpRequestInterceptor>(
				restTemplate.getInterceptors());
		interceptors.add(0, this.interceptor);
		restTemplate.setInterceptors(interceptors);
	}

	private boolean hasTraceInterceptor(RestTemplate restTemplate) {
		for (ClientHttpRequestInterceptor interceptor : restTemplate
				.getInterceptors()) {
			if (interceptor instanceof TracingClientHttpRequestInterceptor) {
				return true;
			}
		}
		return false;
	}
}