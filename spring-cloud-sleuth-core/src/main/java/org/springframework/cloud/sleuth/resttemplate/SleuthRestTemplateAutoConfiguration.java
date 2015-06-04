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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * Autoconfiguration that sets up a {@code RestTemplate} as a bean if one is not present
 * and performs its additional modification via a {@code RestTemplateConfigurer}.
 *
 * @author Marcin Grzejszczak, 4financeIT
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(value = "spring.cloud.sleuth.resttemplate.enabled", matchIfMissing = true)
public class SleuthRestTemplateAutoConfiguration {

	@Configuration
	protected static class RestTemplateConfig {

		@Autowired
		private List<ClientHttpRequestInterceptor> clientHttpRequestInterceptors = new ArrayList<>();

		@Bean
		@ConditionalOnMissingBean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		@ConditionalOnMissingBean
		public RestTemplateConfigurer restTemplateConfigurer(RestTemplate restTemplate) {
			DefaultRestTemplateConfigurer configurer = new DefaultRestTemplateConfigurer(clientHttpRequestInterceptors);
			configurer.modifyRestTemplate(restTemplate);
			return configurer;
		}
	}

}
