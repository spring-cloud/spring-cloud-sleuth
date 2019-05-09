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

import java.util.List;
import java.util.stream.Collectors;

import brave.spring.web.TracingAsyncClientHttpRequestInterceptor;
import brave.spring.web.TracingClientHttpRequestInterceptor;

import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

/**
 *
 * Utility class to ease creation of {@link RestTemplate} - and derived classed - that are
 * not instrumented by sleuth. <br/>
 * This class is intended for creation of the RestTemplate sending data to Zipkin (thus
 * avoiding reporting the reporter).
 *
 * @author natrem
 *
 */
public class SleuthWebClientInterceptorRemover {

	public List<ClientHttpRequestInterceptor> filter(
			List<ClientHttpRequestInterceptor> interceptors) {
		List<ClientHttpRequestInterceptor> filteredInterceptors = interceptors.stream()
				.filter(i -> !isTracingInterceptor(i)).collect(Collectors.toList());
		return filteredInterceptors;
	}

	private boolean isTracingInterceptor(ClientHttpRequestInterceptor interceptor) {
		Class<? extends ClientHttpRequestInterceptor> interceptorClass = interceptor
				.getClass();
		return TracingAsyncClientHttpRequestInterceptor.class
				.isAssignableFrom(interceptorClass)
				|| TracingClientHttpRequestInterceptor.class
						.isAssignableFrom(interceptorClass)
				|| LazyTracingClientHttpRequestInterceptor.class
						.isAssignableFrom(interceptorClass)
				|| AsyncClientHttpRequestInterceptor.class
						.isAssignableFrom(interceptorClass);
	}

}
