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

import org.springframework.cloud.sleuth.SpanAccessor;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AsyncClientHttpRequest;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Wrapper that adds trace related headers to the created {@link AsyncClientHttpRequest}
 * and to the {@link ClientHttpRequest}
 *
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 *
 * @since 1.0.0
 */
public class TraceAsyncClientHttpRequestFactoryWrapper extends AbstractTraceHttpRequestInterceptor
		implements ClientHttpRequestFactory, AsyncClientHttpRequestFactory {

	private final Tracer tracer;
	private final AsyncClientHttpRequestFactory delegate;
	private final ClientHttpRequestFactory syncDelegate;

	/**
	 * According to the JavaDocs all Spring {@link AsyncClientHttpRequestFactory} implement
	 * the {@link ClientHttpRequestFactory} interface.
	 *
	 * In case that it's not true we're setting the {@link SimpleClientHttpRequestFactory}
	 * as a default for sync request processing.
	 *
	 * @see org.springframework.web.client.AsyncRestTemplate#AsyncRestTemplate(AsyncClientHttpRequestFactory)
	 */
	public TraceAsyncClientHttpRequestFactoryWrapper(SpanAccessor accessor, Tracer tracer,
			AsyncClientHttpRequestFactory delegate) {
		super(accessor);
		this.tracer = tracer;
		this.delegate = delegate;
		this.syncDelegate = delegate instanceof ClientHttpRequestFactory ?
				(ClientHttpRequestFactory) delegate : defaultClientHttpRequestFactory();
	}

	/**
	 * Default implementation that creates a {@link SimpleClientHttpRequestFactory} that
	 * has a wrapped task executor via the {@link TraceAsyncListenableTaskExecutor}
	 */
	public TraceAsyncClientHttpRequestFactoryWrapper(SpanAccessor accessor, Tracer tracer) {
		super(accessor);
		this.tracer = tracer;
		SimpleClientHttpRequestFactory simpleClientHttpRequestFactory = defaultClientHttpRequestFactory();
		this.delegate = simpleClientHttpRequestFactory;
		this.syncDelegate = simpleClientHttpRequestFactory;
	}

	public TraceAsyncClientHttpRequestFactoryWrapper(SpanAccessor accessor, Tracer tracer,
			AsyncClientHttpRequestFactory delegate, ClientHttpRequestFactory syncDelegate) {
		super(accessor);
		this.tracer = tracer;
		this.delegate = delegate;
		this.syncDelegate = syncDelegate;
	}

	private SimpleClientHttpRequestFactory defaultClientHttpRequestFactory() {
		SimpleClientHttpRequestFactory simpleClientHttpRequestFactory = new SimpleClientHttpRequestFactory();
		simpleClientHttpRequestFactory.setTaskExecutor(asyncListenableTaskExecutor(this.tracer));
		return simpleClientHttpRequestFactory;
	}

	private AsyncListenableTaskExecutor asyncListenableTaskExecutor(Tracer tracer) {
		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
		threadPoolTaskScheduler.initialize();
		return new TraceAsyncListenableTaskExecutor(threadPoolTaskScheduler, tracer);
	}

	@Override
	public AsyncClientHttpRequest createAsyncRequest(URI uri, HttpMethod httpMethod)
			throws IOException {
		AsyncClientHttpRequest request = this.delegate.createAsyncRequest(uri, httpMethod);
		if (!isTracing()) {
			doNotSampleThisSpan(request);
			return request;
		}
		publishStartEvent(request);
		return request;
	}

	@Override
	public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod)
			throws IOException {
		ClientHttpRequest request = this.syncDelegate.createRequest(uri, httpMethod);
		if (!isTracing()) {
			doNotSampleThisSpan(request);
			return request;
		}
		publishStartEvent(request);
		return request;
	}
}
