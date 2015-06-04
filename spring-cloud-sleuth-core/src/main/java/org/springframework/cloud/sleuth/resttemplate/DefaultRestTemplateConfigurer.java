/*
 * Copyright 2012-2015 the original author or authors.
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
package org.springframework.cloud.sleuth.resttemplate;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Default configure of {@code RestTemplate} that adds a list {@code ClientHttpRequestInterceptor}
 * to {@code RestTemplate}
 *
 * @see ClientHttpRequestInterceptor
 * @see RestTemplate
 *
 * @author Marcin Grzejszczak, 4financeIT
 */
public class DefaultRestTemplateConfigurer implements RestTemplateConfigurer {

	private final List<ClientHttpRequestInterceptor> clientHttpRequestInterceptors;

	public DefaultRestTemplateConfigurer(List<ClientHttpRequestInterceptor> clientHttpRequestInterceptors) {
		this.clientHttpRequestInterceptors = clientHttpRequestInterceptors;
	}

	@Override
	public void modifyRestTemplate(RestTemplate restTemplate) {
		for (ClientHttpRequestInterceptor clientHttpRequestInterceptor : clientHttpRequestInterceptors) {
			restTemplate.getInterceptors().add(clientHttpRequestInterceptor);
		}
	}
}
