package org.springframework.cloud.sleuth.zipkin;

import java.io.Closeable;
import java.io.Flushable;
import java.util.concurrent.TimeUnit;

import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.web.client.RestTemplate;

import zipkin.Span;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Encoding;

/**
 * Submits spans using Zipkin's {@code POST /spans} endpoint.
 *
 * @author Adrian Cole
 * @since 1.0.0
 */
public final class HttpZipkinSpanReporter implements ZipkinSpanReporter, Flushable, Closeable {
	private final RestTemplateSender sender;
	private final AsyncReporter<Span> delegate;

	/**
	 * @param restTemplate {@link RestTemplate} used for sending requests to Zipkin
	 * @param baseUrl       URL of the zipkin query server instance. Like: http://localhost:9411/
	 * @param flushInterval in seconds. 0 implies spans are {@link #flush() flushed} externally.
	 * @param spanMetricReporter service to count number of accepted / dropped spans
	 */
	public HttpZipkinSpanReporter(RestTemplate restTemplate, String baseUrl, int flushInterval,
			SpanMetricReporter spanMetricReporter) {
		this(restTemplate, baseUrl, flushInterval, spanMetricReporter, Encoding.JSON);
	}

	/**
	 * @param restTemplate {@link RestTemplate} used for sending requests to Zipkin
	 * @param baseUrl       URL of the zipkin query server instance. Like: http://localhost:9411/
	 * @param flushInterval in seconds. 0 implies spans are {@link #flush() flushed} externally.
	 * @param spanMetricReporter service to count number of accepted / dropped spans
	 * @param encoding span encoding.
	 */
	public HttpZipkinSpanReporter(RestTemplate restTemplate, String baseUrl, int flushInterval,
								SpanMetricReporter spanMetricReporter, Encoding encoding) {
		this.sender = new RestTemplateSender(restTemplate, baseUrl, encoding);
		this.delegate = AsyncReporter.builder(this.sender)
				.queuedMaxSpans(1000) // historical constraint. Note: AsyncReporter supports memory bounds
				.messageTimeout(flushInterval, TimeUnit.SECONDS)
				.metrics(new ReporterMetricsAdapter(spanMetricReporter))
				.build();
	}

	/**
	 * Queues the span for collection, or drops it if the queue is full.
	 *
	 * @param span Span, should not be <code>null</code>.
	 */
	@Override
	public void report(Span span) {
		this.delegate.report(span);
	}

	/**
	 * Calling this will flush any pending spans to the http transport on the current thread.
	 */
	@Override
	public void flush() {
		this.delegate.flush();
	}

	/**
	 * Blocks until in-flight spans are sent and drops any that are left pending.
	 */
	@Override
	public void close() {
		this.delegate.close();
		this.sender.close();
	}
}
