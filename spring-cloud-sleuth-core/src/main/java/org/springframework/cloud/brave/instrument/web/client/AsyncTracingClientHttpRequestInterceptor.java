package org.springframework.cloud.brave.instrument.web.client;

import java.io.IOException;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.AsyncClientHttpRequestExecution;
import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

/**
 * Interceptor for Async Rest Template
 *
 * @since 2.0.0
 */
public final class AsyncTracingClientHttpRequestInterceptor
		implements AsyncClientHttpRequestInterceptor {
	static final Propagation.Setter<HttpHeaders, String> SETTER = new Propagation.Setter<HttpHeaders, String>() {
		@Override public void put(HttpHeaders carrier, String key, String value) {
			carrier.set(key, value);
		}

		@Override public String toString() {
			return "HttpHeaders::set";
		}
	};

	public static AsyncClientHttpRequestInterceptor create(Tracing tracing) {
		return create(HttpTracing.create(tracing));
	}

	public static AsyncClientHttpRequestInterceptor create(HttpTracing httpTracing) {
		return new AsyncTracingClientHttpRequestInterceptor(httpTracing);
	}

	final Tracer tracer;
	final HttpClientHandler<HttpRequest, ClientHttpResponse> handler;
	final TraceContext.Injector<HttpHeaders> injector;

	private AsyncTracingClientHttpRequestInterceptor(HttpTracing httpTracing) {
		this.tracer = httpTracing.tracing().tracer();
		this.handler = HttpClientHandler.create(httpTracing,
				new AsyncTracingClientHttpRequestInterceptor.HttpAdapter());
		this.injector = httpTracing.tracing().propagation().injector(SETTER);
	}

	@Override public ListenableFuture<ClientHttpResponse> intercept(HttpRequest request,
			byte[] body, AsyncClientHttpRequestExecution execution) throws IOException {
		Span span = this.handler.handleSend(this.injector, request.getHeaders(), request);
		ListenableFuture<ClientHttpResponse> response = null;
		Throwable error = null;
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			return response = execution.executeAsync(request, body);
		}
		catch (IOException | RuntimeException | Error e) {
			error = e;
			throw e;
		}
		finally {
			if (response == null) {
				this.handler.handleReceive(null, error, span);
			}
			else {
				response.addCallback(
						new TraceListenableFutureCallback(span, this.handler));
			}
		}
	}

	static final class HttpAdapter
			extends brave.http.HttpClientAdapter<HttpRequest, ClientHttpResponse> {

		@Override public String method(HttpRequest request) {
			return request.getMethod().name();
		}

		@Override public String url(HttpRequest request) {
			return request.getURI().toString();
		}

		@Override public String requestHeader(HttpRequest request, String name) {
			Object result = request.getHeaders().getFirst(name);
			return result != null ? result.toString() : null;
		}

		@Override public Integer statusCode(ClientHttpResponse response) {
			try {
				return response.getRawStatusCode();
			}
			catch (IOException e) {
				return null;
			}
		}
	}

	static final class TraceListenableFutureCallback
			implements ListenableFutureCallback<ClientHttpResponse> {

		private final Span span;
		private final HttpClientHandler<HttpRequest, ClientHttpResponse> handler;

		private TraceListenableFutureCallback(Span span,
				HttpClientHandler<HttpRequest, ClientHttpResponse> handler) {
			this.span = span;
			this.handler = handler;
		}

		@Override public void onFailure(Throwable ex) {
			this.handler.handleReceive(null, ex, this.span);
		}

		@Override public void onSuccess(ClientHttpResponse result) {
			this.handler.handleReceive(result, null, this.span);
		}
	}
}