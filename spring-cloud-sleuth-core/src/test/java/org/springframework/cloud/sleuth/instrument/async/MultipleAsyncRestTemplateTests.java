/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.async;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpSpanInjector;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.instrument.web.client.TraceAsyncClientHttpRequestFactoryWrapper;
import org.springframework.cloud.sleuth.instrument.web.client.TraceAsyncRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AsyncClientHttpRequest;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.AsyncRestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class) @SpringBootTest(
		classes = { MultipleAsyncRestTemplateTests.Config.class,
				MultipleAsyncRestTemplateTests.CustomExecutorConfig.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MultipleAsyncRestTemplateTests {

	@Autowired @Qualifier("customAsyncRestTemplate") AsyncRestTemplate asyncRestTemplate;
	@Autowired AsyncConfigurer executor;

	@Test
	public void should_start_context_with_custom_async_client() throws Exception {
		then(this.asyncRestTemplate).isNotNull();
	}

	@Test
	public void should_start_context_with_custom_executor() throws Exception {
		then(this.executor).isNotNull();
		then(this.executor.getAsyncExecutor()).isInstanceOf(LazyTraceExecutor.class);
	}

	//tag::custom_async_rest_template[]
	@Configuration
	@EnableAutoConfiguration
	static class Config {
		@Autowired Tracer tracer;
		@Autowired HttpTraceKeysInjector httpTraceKeysInjector;
		@Autowired HttpSpanInjector spanInjector;

		@Bean(name = "customAsyncRestTemplate")
		public AsyncRestTemplate traceAsyncRestTemplate(@Qualifier("customHttpRequestFactoryWrapper")
				TraceAsyncClientHttpRequestFactoryWrapper wrapper, ErrorParser errorParser) {
			return new TraceAsyncRestTemplate(wrapper, this.tracer, errorParser);
		}

		@Bean(name = "customHttpRequestFactoryWrapper")
		public TraceAsyncClientHttpRequestFactoryWrapper traceAsyncClientHttpRequestFactory() {
			return new TraceAsyncClientHttpRequestFactoryWrapper(this.tracer,
					this.spanInjector,
					asyncClientFactory(),
					clientHttpRequestFactory(),
					this.httpTraceKeysInjector);
		}

		private ClientHttpRequestFactory clientHttpRequestFactory() {
			ClientHttpRequestFactory clientHttpRequestFactory = new CustomClientHttpRequestFactory();
			//CUSTOMIZE HERE
			return clientHttpRequestFactory;
		}

		private AsyncClientHttpRequestFactory asyncClientFactory() {
			AsyncClientHttpRequestFactory factory = new CustomAsyncClientHttpRequestFactory();
			//CUSTOMIZE HERE
			return factory;
		}
	}
	//end::custom_async_rest_template[]

	//tag::custom_executor[]
	@Configuration
	@EnableAutoConfiguration
	@EnableAsync
	static class CustomExecutorConfig extends AsyncConfigurerSupport {

		@Autowired BeanFactory beanFactory;

		@Override public Executor getAsyncExecutor() {
			ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
			// CUSTOMIZE HERE
			executor.setCorePoolSize(7);
			executor.setMaxPoolSize(42);
			executor.setQueueCapacity(11);
			executor.setThreadNamePrefix("MyExecutor-");
			// DON'T FORGET TO INITIALIZE
			executor.initialize();
			return new LazyTraceExecutor(this.beanFactory, executor);
		}
	}
	//end::custom_executor[]
}

class CustomClientHttpRequestFactory implements ClientHttpRequestFactory {

	@Override public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod)
			throws IOException {
		return null;
	}
}

class CustomAsyncClientHttpRequestFactory implements AsyncClientHttpRequestFactory {

	@Override
	public AsyncClientHttpRequest createAsyncRequest(URI uri, HttpMethod httpMethod)
			throws IOException {
		return null;
	}
}