package org.springframework.cloud.sleuth.zipkin;

import org.junit.Rule;
import org.junit.Test;
import org.springframework.cloud.sleuth.metric.CounterServiceBasedSpanMetricReporter;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import zipkin.Span;

import zipkin.junit.HttpFailure;
import zipkin.junit.ZipkinRule;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class HttpZipkinSpanReporterTest {

	@Rule public final ZipkinRule zipkin = new ZipkinRule();
	InMemorySpanCounter inMemorySpanCounter = new InMemorySpanCounter();
	SpanMetricReporter spanMetricReporter = new CounterServiceBasedSpanMetricReporter("accepted", "dropped",
			this.inMemorySpanCounter);

	HttpZipkinSpanReporter reporter = new HttpZipkinSpanReporter(
			this.zipkin.httpUrl(),
			0, // so that tests can drive flushing explicitly
			false, // disable compression
			this.spanMetricReporter
	);

	@Test
	public void reportDoesntDoIO() throws Exception {
		this.reporter.report(span(1L, "foo"));

		assertThat(this.zipkin.httpRequestCount()).isZero();
	}

	@Test
	public void reportIncrementsAcceptedMetrics() throws Exception {
		this.reporter.report(span(1L, "foo"));

		assertThat(this.inMemorySpanCounter.getAcceptedSpans()).isEqualTo(1);
		assertThat(this.inMemorySpanCounter.getDroppedSpans()).isZero();
	}

	@Test
	public void dropsWhenQueueIsFull() throws Exception {
		for (int i = 0; i < 1001; i++)
			this.reporter.report(span(1L, "foo"));

		assertThat(this.inMemorySpanCounter.getAcceptedSpans()).isEqualTo(1001);
		assertThat(this.inMemorySpanCounter.getDroppedSpans()).isEqualTo(1);
	}

	@Test
	public void postsSpans() throws Exception {
		this.reporter.report(span(1L, "foo"));
		this.reporter.report(span(2L, "bar"));

		this.reporter.flush(); // manually flush the spans

		// Ensure only one request was sent
		assertThat(this.zipkin.httpRequestCount()).isEqualTo(1);

		assertThat(this.zipkin.getTraces()).containsExactly(
				asList(span(1L, "foo")),
				asList(span(2L, "bar"))
		);
	}

	@Test
	public void postsCompressedSpans() throws Exception {
		this.reporter = new HttpZipkinSpanReporter(
				this.zipkin.httpUrl(),
				0, // so that tests can drive flushing explicitly
				false, // enable compression
				this.spanMetricReporter
		);

		this.reporter.report(span(1L, "foo"));
		this.reporter.report(span(2L, "bar"));

		this.reporter.flush(); // manually flush the spans

		// Ensure only one request was sent
		assertThat(this.zipkin.httpRequestCount()).isEqualTo(1);

		assertThat(this.zipkin.getTraces()).containsExactly(
				asList(span(1L, "foo")),
				asList(span(2L, "bar"))
		);
	}

	@Test 
	public void incrementsDroppedSpansWhenServerErrors() throws Exception {
		this.zipkin.enqueueFailure(HttpFailure.sendErrorResponse(500, "Ouch"));

		this.reporter.report(span(1L, "foo"));
		this.reporter.report(span(2L, "bar"));

		this.reporter.flush(); // manually flush the spans

		assertThat(this.inMemorySpanCounter.getDroppedSpans()).isEqualTo(2);
	}

	@Test
	public void incrementsDroppedSpansWhenServerDisconnects() throws Exception {
		this.zipkin.enqueueFailure(HttpFailure.disconnectDuringBody());

		this.reporter.report(span(1L, "foo"));
		this.reporter.report(span(2L, "bar"));

		this.reporter.flush(); // manually flush the spans

		assertThat(this.inMemorySpanCounter.getDroppedSpans()).isEqualTo(2);
	}

	static Span span(long traceId, String spanName) {
		return Span.builder().traceId(traceId).id(traceId).name(spanName).build();
	}
}
