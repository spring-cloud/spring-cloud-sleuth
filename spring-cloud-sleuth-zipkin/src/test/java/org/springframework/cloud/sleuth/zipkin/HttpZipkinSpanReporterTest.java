package org.springframework.cloud.sleuth.zipkin;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import io.zipkin.Codec;
import io.zipkin.Span;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpZipkinSpanReporterTest {

  @Rule
  public final MockWebServer server = new MockWebServer();

  // set flush interval to 0 so that tests can drive flushing explicitly
  HttpZipkinSpanReporter reporter = new HttpZipkinSpanReporter(server.url("").toString(), 0);

  @Test
  public void reportDoesntDoIO() throws Exception {
    reporter.report(span(1L, "foo"));

    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  public void reportIncrementsAcceptedMetrics() throws Exception {
    reporter.report(span(1L, "foo"));

    // TODO: assertThat(metrics.acceptedSpans.get()).isEqualTo(1);
    // TODO: assertThat(metrics.droppedSpans.get()).isZero();
  }

  @Test
  public void dropsWhenQueueIsFull() throws Exception {
    for (int i = 0; i < 1001; i++)
      reporter.report(span(1L, "foo"));

    // TODO: assertThat(metrics.acceptedSpans.get()).isEqualTo(1001);
    // TODO: assertThat(metrics.droppedSpans.get()).isEqualTo(1);
  }

  @Test
  public void postsSpans() throws Exception {
    server.enqueue(new MockResponse());

    reporter.report(span(1L, "foo"));
    reporter.report(span(2L, "bar"));

    reporter.flush(); // manually flush the spans

    // Ensure a proper request was sent
    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestLine()).isEqualTo("POST /api/v1/spans HTTP/1.1");
    assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");

    // Now, let's read back the spans we sent!
    List<io.zipkin.Span> zipkinSpans = Codec.JSON.readSpans(request.getBody().readByteArray());
    assertThat(zipkinSpans).containsExactly(
        span(1L, "foo"),
        span(2L, "bar")
    );
  }

  @Test
  public void incrementsDroppedSpansWhenServerErrors() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(500));

    reporter.report(span(1L, "foo"));
    reporter.report(span(2L, "bar"));

    reporter.flush(); // manually flush the spans

    // TODO: assertThat(metrics.droppedSpans.get()).isEqualTo(2);
  }

  @Test
  public void incrementsDroppedSpansWhenServerDisconnects() throws Exception {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));

    reporter.report(span(1L, "foo"));
    reporter.report(span(2L, "bar"));

    reporter.flush(); // manually flush the spans

    // TODO: assertThat(metrics.droppedSpans.get()).isEqualTo(2);
  }

  static Span span(long traceId, String spanName) {
    return new io.zipkin.Span.Builder().traceId(traceId).id(traceId).name(spanName).build();
  }
}
