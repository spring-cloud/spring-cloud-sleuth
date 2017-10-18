package org.springframework.cloud.sleuth.zipkin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.metric.CounterServiceBasedSpanMetricReporter;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestTemplate;
import zipkin.Span;
import zipkin.junit.HttpFailure;
import zipkin.junit.ZipkinRule;
import zipkin.reporter.Encoding;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;

public class HttpZipkinSpanReporterTest {

	@Rule public final ZipkinRule zipkin = new ZipkinRule();
	InMemorySpanCounter inMemorySpanCounter = new InMemorySpanCounter();
	SpanMetricReporter spanMetricReporter = new CounterServiceBasedSpanMetricReporter("accepted", "dropped",
			this.inMemorySpanCounter);
	RestTemplate restTemplate = defaultRestTemplate();

	HttpZipkinSpanReporter reporter = new HttpZipkinSpanReporter(restTemplate, this.zipkin.httpUrl(),
			0, // so that tests can drive flushing explicitly
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

	@Test
	public void should_change_the_service_name_in_zipkin_to_the_manually_provided_one() {
		AtomicReference<Span> receivedSpan = new AtomicReference<>();
		Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(), new DefaultSpanNamer(),
				new NoOpSpanLogger(), new ZipkinSpanListener(receivedSpan::set,
				new ServerPropertiesEndpointLocator(new ServerProperties(), new MockEnvironment(),
						new ZipkinProperties(), new InetUtils(new InetUtilsProperties())),
				null, new ArrayList<>()), new TraceKeys());
		// tag::service_name[]
		org.springframework.cloud.sleuth.Span newSpan = tracer.createSpan("redis");
		try {
			newSpan.tag("redis.op", "get");
			newSpan.tag("lc", "redis");
			newSpan.logEvent(org.springframework.cloud.sleuth.Span.CLIENT_SEND);
			// call redis service e.g
			// return (SomeObj) redisTemplate.opsForHash().get("MYHASH", someObjKey);
		} finally {
			newSpan.tag("peer.service", "redisService");
			newSpan.tag("peer.ipv4", "1.2.3.4");
			newSpan.tag("peer.port", "1234");
			newSpan.logEvent(org.springframework.cloud.sleuth.Span.CLIENT_RECV);
			tracer.close(newSpan);
		}
		// end::service_name[]

		then(tracer.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
		then(receivedSpan.get().binaryAnnotations)
				.flatExtracting(input -> input.key, input -> new String(input.value))
				.contains("peer.service", "redisService");
	}

	@Test
	public void testSenderThriftEncoding() {
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		zipkinProperties.setEncoding(Encoding.THRIFT);
		zipkinProperties.setBaseUrl(zipkin.httpUrl());

		HttpZipkinSpanReporter httpZipkinSpanReporter = new HttpZipkinSpanReporter(restTemplate(zipkinProperties)
				, zipkinProperties.getBaseUrl(), 1, spanMetricReporter, zipkinProperties.getEncoding());

		Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(), new DefaultSpanNamer(),
				new NoOpSpanLogger(),new ZipkinSpanListener(httpZipkinSpanReporter,
				new ServerPropertiesEndpointLocator(new ServerProperties(), new MockEnvironment(),
						zipkinProperties, new InetUtils(new InetUtilsProperties())),
				null, Collections.emptyList()), new TraceKeys());

		tracer.close(tracer.createSpan("foo"));
		httpZipkinSpanReporter.flush();

		await().until(() -> zipkin.getTraces().size() == 1);
		assertThat(zipkin.getTraces().size()).isEqualTo(1);
	}

	static Span span(long traceId, String spanName) {
		return Span.builder().traceId(traceId).id(traceId).name(spanName).build();
	}

	private RestTemplate restTemplate(ZipkinProperties zipkinProperties) {
		RestTemplate restTemplate = new RestTemplate();
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
}
