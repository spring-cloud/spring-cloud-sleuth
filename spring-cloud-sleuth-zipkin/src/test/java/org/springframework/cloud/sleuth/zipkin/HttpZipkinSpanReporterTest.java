package org.springframework.cloud.sleuth.zipkin;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.cloud.sleuth.metric.CounterServiceBasedSpanReporterService;
import org.springframework.cloud.sleuth.metric.SpanReporterService;
import zipkin.Codec;
import zipkin.Span;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpZipkinSpanReporterTest {

	@Rule public final MockWebServer server = new MockWebServer();
	InMemorySpanCounter inMemorySpanCounter = new InMemorySpanCounter();
	SpanReporterService spanReporterService = new CounterServiceBasedSpanReporterService("accepted", "dropped",
			this.inMemorySpanCounter);

	// set flush interval to 0 so that tests can drive flushing explicitly
	HttpZipkinSpanReporter reporter = new HttpZipkinSpanReporter(
			this.server.url("").toString(), 0, this.spanReporterService);

	@Test 
	public void reportDoesntDoIO() throws Exception {
		this.reporter.report(span(1L, "foo"));

		assertThat(this.server.getRequestCount()).isZero();
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
		this.server.enqueue(new MockResponse());

		this.reporter.report(span(1L, "foo"));
		this.reporter.report(span(2L, "bar"));

		this.reporter.flush(); // manually flush the spans

		// Ensure a proper request was sent
		RecordedRequest request = this.server.takeRequest();
		assertThat(request.getRequestLine()).isEqualTo("POST /api/v1/spans HTTP/1.1");
		assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");

		// Now, let's read back the spans we sent!
		List<Span> zipkinSpans = Codec.JSON.readSpans(request.getBody().readByteArray());
		assertThat(zipkinSpans).containsExactly(span(1L, "foo"), span(2L, "bar"));
	}

	@Test 
	public void incrementsDroppedSpansWhenServerErrors() throws Exception {
		this.server.enqueue(new MockResponse().setResponseCode(500));

		this.reporter.report(span(1L, "foo"));
		this.reporter.report(span(2L, "bar"));

		this.reporter.flush(); // manually flush the spans

		assertThat(this.inMemorySpanCounter.getDroppedSpans()).isEqualTo(2);
	}

	@Test 
	public void incrementsDroppedSpansWhenServerDisconnects() throws Exception {
		this.server.enqueue(new MockResponse()
				.setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));

		this.reporter.report(span(1L, "foo"));
		this.reporter.report(span(2L, "bar"));

		this.reporter.flush(); // manually flush the spans

		assertThat(this.inMemorySpanCounter.getDroppedSpans()).isEqualTo(2);
	}

	static Span span(long traceId, String spanName) {
		return new Span.Builder().traceId(traceId).id(traceId).name(spanName).build();
	}
}
