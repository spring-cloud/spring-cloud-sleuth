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

import java.net.URI;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.AsyncRequestCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * An {@link AsyncRestTemplate} that closes started spans when a response has been
 * successfully received.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
public class TraceAsyncRestTemplate extends AsyncRestTemplate {

	private final Tracer tracer;

	public TraceAsyncRestTemplate(Tracer tracer) {
		super();
		this.tracer = tracer;
	}

	public TraceAsyncRestTemplate(AsyncListenableTaskExecutor taskExecutor, Tracer tracer) {
		super(taskExecutor);
		this.tracer = tracer;
	}

	public TraceAsyncRestTemplate(AsyncClientHttpRequestFactory asyncRequestFactory,
			Tracer tracer) {
		super(asyncRequestFactory);
		this.tracer = tracer;
	}

	public TraceAsyncRestTemplate(AsyncClientHttpRequestFactory asyncRequestFactory,
			ClientHttpRequestFactory syncRequestFactory, Tracer tracer) {
		super(asyncRequestFactory, syncRequestFactory);
		this.tracer = tracer;
	}

	public TraceAsyncRestTemplate(AsyncClientHttpRequestFactory requestFactory,
			RestTemplate restTemplate, Tracer tracer) {
		super(requestFactory, restTemplate);
		this.tracer = tracer;
	}

	@Override
	protected <T> ListenableFuture<T> doExecute(URI url, HttpMethod method,
			AsyncRequestCallback requestCallback, ResponseExtractor<T> responseExtractor)
			throws RestClientException {
		ListenableFuture<T> future = super.doExecute(url, method, requestCallback, responseExtractor);
		Span span = this.tracer.getCurrentSpan();
		future.addCallback(new TraceListenableFutureCallback<>(this.tracer, span));
		// potential race can happen here
		if (span != null && span.equals(this.tracer.getCurrentSpan())) {
			this.tracer.detach(span);
		}
		return future;
	}

	private static class TraceListenableFutureCallback<T> implements ListenableFutureCallback<T> {

		private final Tracer tracer;
		private final Span parent;

		private TraceListenableFutureCallback(Tracer tracer, Span parent) {
			this.tracer = tracer;
			this.parent = parent;
		}

		@Override
		public void onFailure(Throwable ex) {
			continueSpan();
			this.tracer.addTag(Span.SPAN_ERROR_TAG_NAME, ExceptionUtils.getExceptionMessage(ex));
			finish();
		}

		@Override
		public void onSuccess(T result) {
			continueSpan();
			finish();
		}

		private void continueSpan() {
			this.tracer.continueSpan(this.parent);
		}

		private void finish() {
			if (!isTracing()) {
				return;
			}
			currentSpan().logEvent(Span.CLIENT_RECV);
			this.tracer.close(currentSpan());
		}

		private Span currentSpan() {
			return this.tracer.getCurrentSpan();
		}

		private boolean isTracing() {
			return this.tracer.isTracing();
		}
	}





}
