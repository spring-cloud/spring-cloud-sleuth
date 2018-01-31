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

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import brave.http.HttpTracing;
import brave.spring.web.TracingAsyncClientHttpRequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.web.client.AsyncRestTemplate;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables span information propagation for {@link AsyncClientHttpRequestFactory} and
 * {@link AsyncRestTemplate}
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration
@SleuthWebClientEnabled
@ConditionalOnProperty(value = "spring.sleuth.web.async.client.enabled", matchIfMissing = true)
@ConditionalOnClass(AsyncRestTemplate.class)
@ConditionalOnBean(HttpTracing.class)
@AutoConfigureAfter(TraceWebServletAutoConfiguration.class)
public class TraceWebAsyncClientAutoConfiguration {

	@ConditionalOnBean(AsyncRestTemplate.class)
	static class AsyncRestTemplateConfig {

		@Bean
		public TracingAsyncClientHttpRequestInterceptor asyncTracingClientHttpRequestInterceptor(HttpTracing httpTracing) {
			return (TracingAsyncClientHttpRequestInterceptor) TracingAsyncClientHttpRequestInterceptor.create(httpTracing);
		}

		@Configuration
		protected static class TraceInterceptorConfiguration {

			@Autowired(required = false)
			private Collection<AsyncRestTemplate> restTemplates;

			@Autowired
			private TracingAsyncClientHttpRequestInterceptor clientInterceptor;

			@PostConstruct
			public void init() {
				if (this.restTemplates != null) {
					for (AsyncRestTemplate restTemplate : this.restTemplates) {
						List<AsyncClientHttpRequestInterceptor> interceptors = new ArrayList<>(
								restTemplate.getInterceptors());
						interceptors.add(this.clientInterceptor);
						restTemplate.setInterceptors(interceptors);
					}
				}
			}
		}
	}
}
