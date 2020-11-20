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

package org.springframework.cloud.sleuth.instrument.web.client;

import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

/**
 * Wraps a {@link RestTemplate} in a trace representation.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class TraceRestTemplateCustomizer implements RestTemplateCustomizer {

	private final ClientHttpRequestInterceptor interceptor;

	public TraceRestTemplateCustomizer(ClientHttpRequestInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@Override
	public void customize(RestTemplate restTemplate) {
		new RestTemplateInterceptorInjector(this.interceptor).inject(restTemplate);
	}

}
