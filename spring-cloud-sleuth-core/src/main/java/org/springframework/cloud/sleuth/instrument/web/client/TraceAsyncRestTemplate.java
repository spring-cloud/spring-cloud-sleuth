/*
 * Copyright 2013-2017 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.SuccessCallback;
import org.springframework.web.client.AsyncRequestCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
	private final ErrorParser errorParser;

	public TraceAsyncRestTemplate(Tracer tracer, ErrorParser errorParser) {
		super();
		this.tracer = tracer;
		this.errorParser = errorParser;
	}

	public TraceAsyncRestTemplate(AsyncListenableTaskExecutor taskExecutor,
			Tracer tracer, ErrorParser errorParser) {
		super(taskExecutor);
		this.tracer = tracer;
		this.errorParser = errorParser;
	}

	public TraceAsyncRestTemplate(AsyncClientHttpRequestFactory asyncRequestFactory,
			Tracer tracer, ErrorParser errorParser) {
		super(asyncRequestFactory);
		this.tracer = tracer;
		this.errorParser = errorParser;
	}

	public TraceAsyncRestTemplate(AsyncClientHttpRequestFactory asyncRequestFactory,
			ClientHttpRequestFactory syncRequestFactory, Tracer tracer, ErrorParser errorParser) {
		super(asyncRequestFactory, syncRequestFactory);
		this.tracer = tracer;
		this.errorParser = errorParser;
	}

	public TraceAsyncRestTemplate(AsyncClientHttpRequestFactory requestFactory,
			RestTemplate restTemplate, Tracer tracer, ErrorParser errorParser) {
		super(requestFactory, restTemplate);
		this.tracer = tracer;
		this.errorParser = errorParser;
	}

	@Override
	protected <T> ListenableFuture<T> doExecute(URI url, HttpMethod method,
			AsyncRequestCallback requestCallback, ResponseExtractor<T> responseExtractor)
			throws RestClientException {
		final ListenableFuture<T> future = super.doExecute(url, method, requestCallback, responseExtractor);
		final Span span = this.tracer.getCurrentSpan();
		future.addCallback(new TraceListenableFutureCallback<>(this.tracer, span,
				this.errorParser));
		// potential race can happen here
		if (span != null && span.equals(this.tracer.getCurrentSpan())) {
			Span parent = this.tracer.detach(span);
			if (parent != null) {
				this.tracer.continueSpan(parent);
			}
		}
		return new ListenableFuture<T>() {

			@Override public boolean cancel(boolean mayInterruptIfRunning) {
				return future.cancel(mayInterruptIfRunning);
			}

			@Override public boolean isCancelled() {
				return future.isCancelled();
			}

			@Override public boolean isDone() {
				return future.isDone();
			}

			@Override public T get() throws InterruptedException, ExecutionException {
				return future.get();
			}

			@Override public T get(long timeout, TimeUnit unit)
					throws InterruptedException, ExecutionException, TimeoutException {
				return future.get(timeout, unit);
			}

			@Override
			public void addCallback(ListenableFutureCallback<? super T> callback) {
				future.addCallback(new TraceListenableFutureCallbackWrapper<>(TraceAsyncRestTemplate.this.tracer, span, callback));
			}

			@Override public void addCallback(SuccessCallback<? super T> successCallback,
					FailureCallback failureCallback) {
				future.addCallback(
						new TraceSuccessCallback<>(TraceAsyncRestTemplate.this.tracer, span, successCallback),
						new TraceFailureCallback(TraceAsyncRestTemplate.this.tracer, span, failureCallback));
			}
		};
	}

	private static class TraceSuccessCallback<T> implements SuccessCallback<T> {

		private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

		private final Tracer tracer;
		private final Span parent;
		private final SuccessCallback<T> delegate;

		private TraceSuccessCallback(Tracer tracer, Span parent,
				SuccessCallback<T> delegate) {
			this.tracer = tracer;
			this.parent = parent;
			this.delegate = delegate;
		}

		@Override public void onSuccess(T result) {
			continueSpan();
			if (log.isDebugEnabled()) {
				log.debug("Calling on success of the delegate");
			}
			this.delegate.onSuccess(result);
			finish();
		}

		private void continueSpan() {
			this.tracer.continueSpan(this.parent);
		}

		private void finish() {
			this.tracer.detach(currentSpan());
		}

		private Span currentSpan() {
			return this.tracer.getCurrentSpan();
		}
	}

	private static class TraceFailureCallback implements FailureCallback {

		private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

		private final Tracer tracer;
		private final Span parent;
		private final FailureCallback delegate;

		private TraceFailureCallback(Tracer tracer, Span parent,
				FailureCallback delegate) {
			this.tracer = tracer;
			this.parent = parent;
			this.delegate = delegate;
		}

		@Override public void onFailure(Throwable ex) {
			continueSpan();
			if (log.isDebugEnabled()) {
				log.debug("Calling on failure of the delegate");
			}
			this.delegate.onFailure(ex);
			finish();
		}

		private void continueSpan() {
			this.tracer.continueSpan(this.parent);
		}

		private void finish() {
			this.tracer.detach(currentSpan());
		}

		private Span currentSpan() {
			return this.tracer.getCurrentSpan();
		}
	}

	private static class TraceListenableFutureCallbackWrapper<T> implements ListenableFutureCallback<T> {

		private final Tracer tracer;
		private final Span parent;
		private final ListenableFutureCallback<T> delegate;

		private TraceListenableFutureCallbackWrapper(Tracer tracer, Span parent,
				ListenableFutureCallback<T> delegate) {
			this.tracer = tracer;
			this.parent = parent;
			this.delegate = delegate;
		}

		@Override public void onFailure(Throwable ex) {
			new TraceFailureCallback(this.tracer, this.parent, this.delegate).onFailure(ex);
		}

		@Override public void onSuccess(T result) {
			new TraceSuccessCallback<>(this.tracer, this.parent, this.delegate).onSuccess(result);
		}
	}

	private static class TraceListenableFutureCallback<T> implements ListenableFutureCallback<T> {

		private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

		private final Tracer tracer;
		private final Span parent;
		private final ErrorParser errorParser;

		private TraceListenableFutureCallback(Tracer tracer, Span parent,
				ErrorParser errorParser) {
			this.tracer = tracer;
			this.parent = parent;
			this.errorParser = errorParser;
		}

		@Override
		public void onFailure(Throwable ex) {
			continueSpan();
			if (log.isDebugEnabled()) {
				log.debug("The callback failed - will close the span");
			}
			this.errorParser.parseErrorTags(currentSpan(), ex);
			finish();
		}

		@Override
		public void onSuccess(T result) {
			continueSpan();
			if (log.isDebugEnabled()) {
				log.debug("The callback succeeded - will close the span");
			}
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
