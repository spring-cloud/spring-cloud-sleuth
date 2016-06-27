package org.springframework.cloud.sleuth.zipkin;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.springframework.cloud.sleuth.metric.CounterServiceBasedSpanMetricReporter;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

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
	RestTemplate restTemplate = defaultRestTemplate();

	HttpZipkinSpanReporter reporter = new HttpZipkinSpanReporter(restTemplate, this.zipkin.httpUrl(),
			0, // so that tests can drive flushing explicitly
			false,
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
		this.reporter = new HttpZipkinSpanReporter(restTemplateWithCompression(), this.zipkin.httpUrl(),
				0, // so that tests can drive flushing explicitly
				true,
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
	public void postsSpansWithBasicAuthentication() throws Exception {
		this.reporter = new HttpZipkinSpanReporter(restTemplateWithBasicAuthentication(), this.zipkin.httpUrl(),
				0, // so that tests can drive flushing explicitly
				false,
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

	private RestTemplate restTemplate(ZipkinProperties zipkinProperties) {
		RestTemplate restTemplate = new RestTemplate(new DefaultZipkinConnectionFactory(zipkinProperties));
		new DefaultZipkinRestTemplateCustomizer(zipkinProperties).customize(restTemplate);
		return restTemplate;
	}

	private RestTemplate defaultRestTemplate() {
		return restTemplate(new ZipkinProperties());
	}

	private RestTemplate restTemplateWithCompression() {
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		zipkinProperties.getCompression().setEnabled(true);
		return restTemplate(zipkinProperties);
	}

	private RestTemplate restTemplateWithBasicAuthentication() {
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		zipkinProperties.setUsername("user");
		zipkinProperties.setPassword("pass");
		RestTemplate restTemplate = restTemplate(zipkinProperties);
		restTemplate.getInterceptors().add(assertingInterceptor());
		return restTemplate;
	}

	ClientHttpRequestInterceptor assertingInterceptor() {
		return (request, body, execution) -> {
			List<String> authorization = request.getHeaders().get("Authorization");
			assertThat(authorization).isNotEmpty();
			// encoded Basic user:pass
			assertThat(authorization.get(0)).isEqualTo("Basic dXNlcjpwYXNz");
			return execution.execute(request, body);
		};
	}

}
