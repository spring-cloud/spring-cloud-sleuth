package org.springframework.cloud.sleuth.zipkin;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.logging.Log;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import zipkin.Codec;
import zipkin.Span;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Submits spans using Zipkin's {@code POST /spans} endpoint.
 *
 * @author Adrian Cole
 * @since 1.0.0
 */
public final class HttpZipkinSpanReporter
		implements ZipkinSpanReporter, Flushable, Closeable {
	private static final Log log = org.apache.commons.logging.LogFactory
			.getLog(HttpZipkinSpanReporter.class);
	private static final Charset UTF_8 = Charset.forName("UTF-8");

	private final RestTemplate restTemplate;
	private final String url;
	private final BlockingQueue<Span> pending = new LinkedBlockingQueue<>(1000);
	private final Flusher flusher; // Nullable for testing
	private final SpanMetricReporter spanMetricReporter;

	/**
	 * @param restTemplate {@link RestTemplate} used for sending requests to Zipkin
	 * @param baseUrl       URL of the zipkin query server instance. Like: http://localhost:9411/
	 * @param flushInterval in seconds. 0 implies spans are {@link #flush() flushed} externally.
	 * @param spanMetricReporter service to count number of accepted / dropped spans
	 */
	public HttpZipkinSpanReporter(RestTemplate restTemplate, String baseUrl, int flushInterval,
			SpanMetricReporter spanMetricReporter) {
		this.restTemplate = restTemplate;
		this.url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v1/spans";
		this.flusher = flushInterval > 0 ? new Flusher(this, flushInterval) : null;
		this.spanMetricReporter = spanMetricReporter;
	}

	/**
	 * Queues the span for collection, or drops it if the queue is full.
	 *
	 * @param span Span, should not be <code>null</code>.
	 */
	@Override
	public void report(Span span) {
		this.spanMetricReporter.incrementAcceptedSpans(1);
		if (!this.pending.offer(span)) {
			this.spanMetricReporter.incrementDroppedSpans(1);
		}
	}

	/**
	 * Calling this will flush any pending spans to the http transport on the current thread.
	 */
	@Override
	public void flush() {
		if (this.pending.isEmpty())
			return;
		List<Span> drained = new ArrayList<>(this.pending.size());
		this.pending.drainTo(drained);
		if (drained.isEmpty())
			return;

		// json-encode the spans for transport
		byte[] json = Codec.JSON.writeSpans(drained);
		// NOTE: https://github.com/openzipkin/zipkin-java/issues/66 will throw instead of return null.
		if (json == null) {
			log.debug("failed to encode spans, dropping them: " + drained);
			this.spanMetricReporter.incrementDroppedSpans(drained.size());
			return;
		}

		// Send the json to the zipkin endpoint
		try {
			postSpans(json);
		}
		catch (RestClientException e) {
			if (log.isDebugEnabled()) { // don't pollute logs unless debug is on.
				// TODO: logger test
				log.debug(
						"error POSTing spans to " + this.url + ": as json: " + new String(json,
								UTF_8), e);
			}
			this.spanMetricReporter.incrementDroppedSpans(drained.size());
		}
	}

	/**
	 * Calls flush on a fixed interval
	 */
	static final class Flusher implements Runnable {
		final Flushable flushable;
		final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

		Flusher(Flushable flushable, int flushInterval) {
			this.flushable = flushable;
			this.scheduler.scheduleWithFixedDelay(this, 0, flushInterval, SECONDS);
		}

		@Override
		public void run() {
			try {
				this.flushable.flush();
			}
			catch (IOException ignored) {
			}
		}
	}

	void postSpans(byte[] json) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		RequestEntity<byte[]> requestEntity = new RequestEntity<>(json, httpHeaders, HttpMethod.POST, URI.create(this.url));
		this.restTemplate.exchange(requestEntity, String.class);
	}

	/**
	 * Requests a cease of delivery. There will be at most one in-flight request processing after this
	 * call returns.
	 */
	@Override
	public void close() {
		if (this.flusher != null)
			this.flusher.scheduler.shutdown();
		// throw any outstanding spans on the floor
		int dropped = this.pending.drainTo(new LinkedList<>());
		this.spanMetricReporter.incrementDroppedSpans(dropped);
	}
}
