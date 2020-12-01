/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.io.IOException;

import brave.spring.web.TracingClientHttpRequestInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class LazyTracingClientHttpRequestInterceptorTests {

	@Test
	void should_not_retrieve_bean_from_context_when_context_is_not_ready()
			throws IOException {
		BeanFactory beanFactory = mock(BeanFactory.class);
		LazyTracingClientHttpRequestInterceptor interceptor = new LazyTracingClientHttpRequestInterceptor(
				beanFactory);

		interceptor.intercept(mock(HttpRequest.class), new byte[0],
				mock(ClientHttpRequestExecution.class));

		then(beanFactory).should(never())
				.getBean(TracingClientHttpRequestInterceptor.class);
	}

	@Test
	void should_retrieve_bean_from_context_when_context_is_ready() throws IOException {
		BeanFactory beanFactory = mock(BeanFactory.class);
		ClientHttpRequestInterceptor requestInterceptor = mock(
				ClientHttpRequestInterceptor.class);
		LazyTracingClientHttpRequestInterceptor interceptor = new LazyTracingClientHttpRequestInterceptor(
				beanFactory) {
			@Override
			ClientHttpRequestInterceptor interceptor() {
				return requestInterceptor;
			}

			@Override
			boolean isContextUnusable() {
				return false;
			}
		};

		interceptor.intercept(mock(HttpRequest.class), new byte[0],
				mock(ClientHttpRequestExecution.class));

		then(requestInterceptor).should().intercept(BDDMockito.any(HttpRequest.class),
				BDDMockito.any(byte[].class),
				BDDMockito.any(ClientHttpRequestExecution.class));
	}

}
