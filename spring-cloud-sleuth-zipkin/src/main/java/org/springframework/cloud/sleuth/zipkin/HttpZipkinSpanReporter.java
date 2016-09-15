package org.springframework.cloud.sleuth.zipkin;

import java.io.Closeable;
import java.io.Flushable;
import java.util.concurrent.TimeUnit;

import org.springframework.cloud.sleuth.metric.SpanMetricReporter;

import zipkin.Span;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.urlconnection.URLConnectionSender;


/**
 * Submits spans using Zipkin's {@code POST /spans} endpoint.
 *
 * @author Adrian Cole
 * @since 1.0.0
 */
public final class HttpZipkinSpanReporter implements ZipkinSpanReporter, Flushable, Closeable {
	private final URLConnectionSender sender;
	private final AsyncReporter<Span> delegate;

	/**
	 * @param baseUrl       URL of the zipkin query server instance. Like: http://localhost:9411/
	 * @param flushInterval in seconds. 0 implies spans are {@link #flush() flushed} externally.
	 * @param compressionEnabled compress spans using gzip before posting to the zipkin server.
	 * @param spanMetricReporter service to count number of accepted / dropped spans
	 */
	public HttpZipkinSpanReporter(String baseUrl, int flushInterval, boolean compressionEnabled,
			SpanMetricReporter spanMetricReporter) {
		this.sender = URLConnectionSender.builder()
				.endpoint(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v1/spans")
				.compressionEnabled(compressionEnabled)
				.build();
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
