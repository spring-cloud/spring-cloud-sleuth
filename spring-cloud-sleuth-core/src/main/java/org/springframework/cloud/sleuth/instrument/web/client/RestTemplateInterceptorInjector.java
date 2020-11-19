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

import java.util.ArrayList;
import java.util.List;

import org.springframework.cloud.sleuth.instrument.web.mvc.TracingClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

class RestTemplateInterceptorInjector {

	private final ClientHttpRequestInterceptor interceptor;

	RestTemplateInterceptorInjector(ClientHttpRequestInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	void inject(RestTemplate restTemplate) {
		if (hasTraceInterceptor(restTemplate)) {
			return;
		}
		List<ClientHttpRequestInterceptor> interceptors = new ArrayList<ClientHttpRequestInterceptor>(
				restTemplate.getInterceptors());
		interceptors.add(0, this.interceptor);
		restTemplate.setInterceptors(interceptors);
	}

	private boolean hasTraceInterceptor(RestTemplate restTemplate) {
		for (ClientHttpRequestInterceptor interceptor : restTemplate.getInterceptors()) {
			if (interceptor instanceof TracingClientHttpRequestInterceptor
					|| interceptor instanceof LazyTraceClientHttpRequestInterceptor) {
				return true;
			}
		}
		return false;
	}

}
