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
import java.net.URI;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAccessor;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AsyncClientHttpRequest;
import org.springframework.http.client.AsyncClientHttpRequestFactory;

/**
 * Wrapper that adds trace related headers to the created AsyncClientHttpRequest
 *
 * @see org.springframework.web.client.RestTemplate
 * @see SpanAccessor
 *
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 */
public class TraceAsyncClientHttpRequestFactoryWrapper extends AbstractTraceHttpRequestInterceptor
		implements AsyncClientHttpRequestFactory {

	private final AsyncClientHttpRequestFactory delegate;

	public TraceAsyncClientHttpRequestFactoryWrapper(SpanAccessor accessor,
			AsyncClientHttpRequestFactory delegate) {
		super(accessor);
		this.delegate = delegate;
	}

	@Override
	public AsyncClientHttpRequest createAsyncRequest(URI uri, HttpMethod httpMethod)
			throws IOException {
		AsyncClientHttpRequest request = this.delegate.createAsyncRequest(uri, httpMethod);
		Span span = getCurrentSpan();
		if (span == null) {
			setHeader(request, Span.NOT_SAMPLED_NAME, "true");
			return request;
		}
		enrichWithTraceHeaders(request, span);
		publish(new ClientSentEvent(this, span));
		return request;
	}
}
