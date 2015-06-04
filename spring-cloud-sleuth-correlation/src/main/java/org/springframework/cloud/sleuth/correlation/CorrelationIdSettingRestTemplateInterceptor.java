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
package org.springframework.cloud.sleuth.correlation;

import lombok.SneakyThrows;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Interceptor that verifies whether the correlation id has been
 * set on the request and sets it if it's missing.
 *
 * @see org.springframework.web.client.RestTemplate
 * @see CorrelationIdHolder
 *
 * @author Marcin Grzejszczak, 4financeIT
 */
public class CorrelationIdSettingRestTemplateInterceptor implements ClientHttpRequestInterceptor {

	@SneakyThrows
	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
		appendCorrelationIdToRequestIfMissing(request);
		ClientHttpResponse response = null;
		try {
			response = execution.execute(request, body);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
		return response;
	}

	private void appendCorrelationIdToRequestIfMissing(HttpRequest request) {
		if (!request.getHeaders().containsKey(CorrelationIdHolder.CORRELATION_ID_HEADER)) {
			request.getHeaders().add(CorrelationIdHolder.CORRELATION_ID_HEADER, CorrelationIdHolder.get());
		}
	}

}
