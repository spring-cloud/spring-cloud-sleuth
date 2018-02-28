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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import brave.spring.web.TracingClientHttpRequestInterceptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TraceWebClientAutoConfigurationTests.Config.class)
public class TraceWebClientAutoConfigurationTests {

	@Autowired @Qualifier("firstRestTemplate") RestTemplate restTemplate;
	@Autowired @Qualifier("secondRestTemplate") RestTemplate secondRestTemplate;
	@Autowired RestTemplateBuilder builder;

	@Test
	public void should_add_rest_template_interceptors() {
 		assertInterceptorsOrder(assertInterceptorsNotEmpty(this.restTemplate));
		assertInterceptorsOrder(assertInterceptorsNotEmpty(this.secondRestTemplate));
		assertInterceptorsOrder(assertInterceptorsNotEmpty(this.builder.build()));
	}

	private List<ClientHttpRequestInterceptor> assertInterceptorsNotEmpty(RestTemplate restTemplate) {
		then(restTemplate).isNotNull();
		List<ClientHttpRequestInterceptor> interceptors = restTemplate
				.getInterceptors();
		then(interceptors).isNotEmpty();
		return interceptors;
	}

	private void assertInterceptorsOrder(
			List<ClientHttpRequestInterceptor> interceptors) {
		int traceInterceptorIndex = -1;
		int myInterceptorIndex = -1;
		int mySecondInterceptorIndex = -1;
		for (int i = 0; i < interceptors.size(); i++) {
			ClientHttpRequestInterceptor interceptor = interceptors
					.get(i);
			if (interceptor instanceof TracingClientHttpRequestInterceptor ||
					interceptor instanceof LazyTracingClientHttpRequestInterceptor) {
				traceInterceptorIndex = i;
			} else if (interceptor instanceof MyClientHttpRequestInterceptor) {
				myInterceptorIndex = i;
			} else if (interceptor instanceof MySecondClientHttpRequestInterceptor) {
				mySecondInterceptorIndex = i;
			}
		}
		then(traceInterceptorIndex)
				.isGreaterThanOrEqualTo(0)
				.isLessThan(myInterceptorIndex)
				.isLessThan(mySecondInterceptorIndex);
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config {

		// custom builder
		@Bean
		RestTemplateBuilder myRestTemplateBuilder(List<RestTemplateCustomizer> customizers) {
			return new RestTemplateBuilder()
					.additionalCustomizers(customizers)
					.additionalInterceptors(new MyClientHttpRequestInterceptor());
		}

		// rest template from builder
		@Bean
		@Qualifier("firstRestTemplate")
		RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
			return restTemplateBuilder
					.build();
		}

		// manual rest template
		@Bean
		@Qualifier("secondRestTemplate")
		RestTemplate secondRestTemplate() {
			RestTemplate restTemplate = new RestTemplate();
			restTemplate.setInterceptors(
					Arrays.asList(new MyClientHttpRequestInterceptor(),
							new MySecondClientHttpRequestInterceptor()));
			return restTemplate;
		}

		// custom customizer
		@Bean
		RestTemplateCustomizer myRestTemplateCustomizer() {
			return restTemplate -> {
				restTemplate.getInterceptors().add(0, new MySecondClientHttpRequestInterceptor());
			};
		}

	}
}

class MyClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

	@Override public ClientHttpResponse intercept(HttpRequest request, byte[] body,
			ClientHttpRequestExecution execution) throws IOException {
		return execution.execute(request, body);
	}
}

class MySecondClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

	@Override public ClientHttpResponse intercept(HttpRequest request, byte[] body,
			ClientHttpRequestExecution execution) throws IOException {
		return execution.execute(request, body);
	}
}