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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.io.IOException;

import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Interceptor that verifies whether the trance and span id has been set on the request
 * and sets them if one or both of them are missing.
 *
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @since 1.0.0
 *
 * @see org.springframework.web.client.RestTemplate
 */
public class TraceRestTemplateInterceptor extends AbstractTraceHttpRequestInterceptor
		implements ClientHttpRequestInterceptor {

	public TraceRestTemplateInterceptor(Tracer tracer, SpanInjector<HttpRequest> spanInjector,
			HttpTraceKeysInjector httpTraceKeysInjector) {
		super(tracer, spanInjector, httpTraceKeysInjector);
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body,
			ClientHttpRequestExecution execution) throws IOException {
		publishStartEvent(request);
		return response(request, body, execution);
	}

	private ClientHttpResponse response(HttpRequest request, byte[] body,
			ClientHttpRequestExecution execution) throws IOException {
		try {
			return new TraceHttpResponse(this, execution.execute(request, body));
		} catch (Exception e) {
			if (log.isDebugEnabled()) {
				log.debug("Exception occurred while trying to execute the request. Will close the span [" + currentSpan() + "]", e);
			}
			this.tracer.close(currentSpan());
			throw e;
		}
	}

}
