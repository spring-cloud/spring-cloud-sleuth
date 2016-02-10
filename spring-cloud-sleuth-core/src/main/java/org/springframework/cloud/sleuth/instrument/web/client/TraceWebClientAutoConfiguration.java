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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.SpanAccessor;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.client.enabled", matchIfMissing = true)
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnBean(SpanAccessor.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
public class TraceWebClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public TraceRestTemplateInterceptor traceRestTemplateInterceptor(SpanAccessor accessor) {
		return new TraceRestTemplateInterceptor(accessor);
	}

	@Bean
	@ConditionalOnMissingBean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}


	@Bean
	@ConditionalOnMissingBean
	public AsyncClientHttpRequestFactory asyncClientHttpRequestFactory(Tracer tracer) {
		SimpleClientHttpRequestFactory simpleClientHttpRequestFactory = new SimpleClientHttpRequestFactory();
		simpleClientHttpRequestFactory.setTaskExecutor(asyncListenableTaskExecutor(tracer));
		return simpleClientHttpRequestFactory;
	}

	private AsyncListenableTaskExecutor asyncListenableTaskExecutor(Tracer tracer) {
		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
		threadPoolTaskScheduler.initialize();
		return new TraceAsyncListenableTaskExecutor(threadPoolTaskScheduler, tracer);
	}

	@Bean
	@ConditionalOnMissingBean
	public AsyncRestTemplate asyncRestTemplate(SpanAccessor spanAccessor,
			AsyncClientHttpRequestFactory asyncClientHttpRequestFactory, RestTemplate restTemplate) {
		return new AsyncRestTemplate(new TraceAsyncClientHttpRequestFactoryWrapper(
				spanAccessor, asyncClientHttpRequestFactory), restTemplate);
	}

	@Configuration
	protected static class TraceInterceptorConfiguration {

		@Autowired(required = false)
		private Collection<RestTemplate> restTemplates;

		@Autowired
		private TraceRestTemplateInterceptor traceRestTemplateInterceptor;

		@PostConstruct
		public void init() {
			if (this.restTemplates != null) {
				for (RestTemplate restTemplate : this.restTemplates) {
					List<ClientHttpRequestInterceptor> interceptors = new ArrayList<ClientHttpRequestInterceptor>(restTemplate.getInterceptors());
					interceptors.add(this.traceRestTemplateInterceptor);
					restTemplate.setInterceptors(interceptors);
				}
			}
		}
	}
}
