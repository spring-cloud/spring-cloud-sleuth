package org.springframework.cloud.sleuth.instrument.web;

import java.net.URL;
import java.util.Arrays;
import java.util.Random;

import org.junit.Test;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * Test case for HttpTraceKeysInjector
 * 
 * @author Sven Zethelius
 */
public class HttpTraceKeysInjectorUnitTests {
	private TraceKeys traceKeys = new TraceKeys();
	private Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(), new DefaultSpanNamer(),
			new NoOpSpanLogger(), new NoOpSpanReporter(), traceKeys);
	private HttpTraceKeysInjector injector = new HttpTraceKeysInjector(tracer, traceKeys);

	@Test
	public void should_set_tags_on_span_with_proper_header_values() throws Exception {
		Span span = tracer.createSpan("TestSpan");
		URL url = new URL("http://localhost:8080/");
		HttpHeaders headers = new HttpHeaders();
		headers.add("User-Agent", "Test");
		headers.put("Accept", Arrays.asList(MediaType.TEXT_PLAIN_VALUE, MediaType.TEXT_XML_VALUE));
		headers.add("Content-Length", "0");
		headers.add(Span.TRACE_ID_NAME,"3bb9a1fa9e70fdbd763261a53162f330");
		headers.add(Span.SPAN_ID_NAME, "763261a53162f330");
		headers.add(Span.SAMPLED_NAME,"1");
		headers.add(Span.SPAN_NAME_NAME, "http:/");
		this.traceKeys.getHttp().setHeaders(Arrays.asList("Accept", "User-Agent", "Content-Type"));

		this.injector.addRequestTags(url.toString(), url.getHost(), url.getPath(), HttpMethod.GET.name(), headers );

		tracer.close(span);
		then(span.tags())
			.containsEntry("http.user-agent", "Test")
			.containsEntry("http.accept", "'text/plain','text/xml'")
			.doesNotContainKey("http.content-type");
		then(tracer.getCurrentSpan()).isNull();
	}
}
