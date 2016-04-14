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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.SpanAccessor;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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
@ConditionalOnProperty(value = "spring.sleuth.web.async.client.enabled", matchIfMissing = true)
@ConditionalOnClass(AsyncRestTemplate.class)
@ConditionalOnBean(SpanAccessor.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
public class TraceWebAsyncClientAutoConfiguration {

	@Autowired Tracer tracer;
	@Autowired(required = false) ClientHttpRequestFactory clientHttpRequestFactory;
	@Autowired(required = false) AsyncClientHttpRequestFactory asyncClientHttpRequestFactory;

	@Bean
	@Primary
	public AsyncClientHttpRequestFactory traceAsyncClientHttpRequestFactory(Tracer tracer,
			SpanInjector<HttpRequest> spanInjector) {
		ClientHttpRequestFactory clientFactory = this.clientHttpRequestFactory;
		AsyncClientHttpRequestFactory asyncClientFactory = this.asyncClientHttpRequestFactory;
		if (clientFactory == null) {
			clientFactory = defaultClientHttpRequestFactory(tracer);
		}
		if (asyncClientFactory == null) {
			asyncClientFactory = clientFactory instanceof AsyncClientHttpRequestFactory ?
					(AsyncClientHttpRequestFactory) clientFactory : defaultClientHttpRequestFactory(tracer);
		}
		return new TraceAsyncClientHttpRequestFactoryWrapper(tracer, spanInjector, asyncClientFactory, clientFactory);
	}

	private SimpleClientHttpRequestFactory defaultClientHttpRequestFactory(Tracer tracer) {
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
	public AsyncRestTemplate asyncRestTemplate(AsyncClientHttpRequestFactory asyncClientHttpRequestFactory,
			Tracer tracer) {
		return new TraceAsyncRestTemplate(asyncClientHttpRequestFactory, tracer);
	}

}
