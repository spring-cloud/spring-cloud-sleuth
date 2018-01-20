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

package org.springframework.cloud.sleuth.zipkin2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

/**
 * Default {@link ZipkinRestTemplateCustomizer} that provides the GZip compression if
 * {@link ZipkinProperties#compression} is enabled.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.1.0
 */
public class DefaultZipkinRestTemplateCustomizer implements ZipkinRestTemplateCustomizer {
	private final ZipkinProperties zipkinProperties;

	public DefaultZipkinRestTemplateCustomizer(
			ZipkinProperties zipkinProperties) {
		this.zipkinProperties = zipkinProperties;
	}

	@Override
	public void customize(RestTemplate restTemplate) {
		if (this.zipkinProperties.getCompression().isEnabled()) {
			restTemplate.getInterceptors().add(0, new GZipInterceptor());
		}
	}

	private class GZipInterceptor implements ClientHttpRequestInterceptor {

		public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws
				IOException {
			request.getHeaders().add("Content-Encoding", "gzip");
			ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
			try (GZIPOutputStream compressor = new GZIPOutputStream(gzipped)) {
				compressor.write(body);
			}
			return execution.execute(request, gzipped.toByteArray());
		}
	}
}
