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

package org.springframework.cloud.sleuth.zipkin;

import java.io.IOException;
import java.nio.charset.Charset;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Base64Utils;
import org.springframework.web.client.RestTemplate;

/**
 * Default {@link ZipkinRestTemplateCustomizer} that adds Basic Authentication interceptor
 * to the {@link RestTemplate} responsible for sending spans to Zipkin.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.1.0
 */
class DefaultZipkinRestTemplateCustomizer implements ZipkinRestTemplateCustomizer {
	private final ZipkinProperties zipkinProperties;

	public DefaultZipkinRestTemplateCustomizer(
			ZipkinProperties zipkinProperties) {
		this.zipkinProperties = zipkinProperties;
	}

	@Override
	public void customize(RestTemplate restTemplate) {
		restTemplate.setRequestFactory(new DefaultZipkinConnectionFactory(this.zipkinProperties));
		if (this.zipkinProperties.isBasicAuthenticated()) {
			restTemplate.getInterceptors().add(
					new BasicAuthorizationInterceptor(this.zipkinProperties.getUsername(), this.zipkinProperties.getPassword()));
		}
	}

	private class BasicAuthorizationInterceptor implements ClientHttpRequestInterceptor {
		private final String username;
		private final String password;

		BasicAuthorizationInterceptor(String username, String password) {
			this.username = username;
			this.password = password == null ? "" : password;
		}

		public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws
				IOException {
			String token = Base64Utils.encodeToString((this.username + ":" + this.password).getBytes(
					Charset.forName("UTF-8")));
			request.getHeaders().add("Authorization", "Basic " + token);
			return execution.execute(request, body);
		}
	}
}
