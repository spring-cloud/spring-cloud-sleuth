/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  https://www.apache.org/licenses/LICENSE-2.0
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
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.instrument.web.HttpSpanInjector;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.instrument.web.TraceWebAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncListenableTaskExecutor;
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
@SleuthWebClientEnabled
@ConditionalOnProperty(value = "spring.sleuth.web.async.client.enabled", matchIfMissing = true)
@ConditionalOnClass(AsyncRestTemplate.class)
@ConditionalOnBean(HttpTraceKeysInjector.class)
@AutoConfigureAfter(TraceWebAutoConfiguration.class)
public class TraceWebAsyncClientAutoConfiguration {

	@Autowired Tracer tracer;
	@Autowired private HttpTraceKeysInjector httpTraceKeysInjector;
	@Autowired private HttpSpanInjector spanInjector;
	@Autowired(required = false) private ClientHttpRequestFactory clientHttpRequestFactory;
	@Autowired(required = false) private AsyncClientHttpRequestFactory asyncClientHttpRequestFactory;

	private TraceAsyncClientHttpRequestFactoryWrapper traceAsyncClientHttpRequestFactory() {
		ClientHttpRequestFactory clientFactory = this.clientHttpRequestFactory;
		AsyncClientHttpRequestFactory asyncClientFactory = this.asyncClientHttpRequestFactory;
		if (clientFactory == null) {
			clientFactory = defaultClientHttpRequestFactory(this.tracer);
		}
		if (asyncClientFactory == null) {
			asyncClientFactory = clientFactory instanceof AsyncClientHttpRequestFactory ?
					(AsyncClientHttpRequestFactory) clientFactory : defaultClientHttpRequestFactory(this.tracer);
		}
		return new TraceAsyncClientHttpRequestFactoryWrapper(this.tracer, this.spanInjector,
				asyncClientFactory, clientFactory, this.httpTraceKeysInjector);
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
	@ConditionalOnProperty(value = "spring.sleuth.web.async.client.template.enabled", matchIfMissing = true)
	public AsyncRestTemplate traceAsyncRestTemplate(ErrorParser errorParser) {
		return new TraceAsyncRestTemplate(traceAsyncClientHttpRequestFactory(), this.tracer, errorParser);
	}

}
