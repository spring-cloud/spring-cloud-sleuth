/*
 * Copyright 2013-2015 the original author or authors.
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

import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Implementation of {@link ClientHttpResponse} that upon
 * {@link ClientHttpResponse#close() closing the response}
 * {@link TraceRestTemplateInterceptor#finish() closes the span}
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class TraceHttpResponse implements ClientHttpResponse {

	private final ClientHttpResponse delegate;
	private final TraceRestTemplateInterceptor interceptor;

	public TraceHttpResponse(TraceRestTemplateInterceptor interceptor,
			ClientHttpResponse delegate) {
		this.interceptor = interceptor;
		this.delegate = delegate;
	}

	@Override
	public HttpHeaders getHeaders() {
		return this.delegate.getHeaders();
	}

	@Override
	public InputStream getBody() throws IOException {
		return this.delegate.getBody();
	}

	@Override
	public HttpStatus getStatusCode() throws IOException {
		return this.delegate.getStatusCode();
	}

	@Override
	public int getRawStatusCode() throws IOException {
		return this.delegate.getRawStatusCode();
	}

	@Override
	public String getStatusText() throws IOException {
		return this.delegate.getStatusText();
	}

	@Override
	public void close() {
		try {
			this.delegate.close();
		}
		finally {
			this.interceptor.finish();
		}
	}
}
