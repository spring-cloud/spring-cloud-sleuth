package org.springframework.cloud.sleuth.instrument.web;

import static org.assertj.core.api.BDDAssertions.*;

import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import org.junit.Test;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.log.Slf4jSpanLogger;
import org.springframework.cloud.sleuth.log.SpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;


/**
 * Test case for HttpTraceKeysInjector
 * 
 * @author Sven Zethelius
 */
public class HttpTraceKeysInjectorUnitTests {
	private TraceKeys traceKeys = new TraceKeys();
	private SpanNamer spanNamer = new DefaultSpanNamer();
	private SpanLogger spanLogger = new Slf4jSpanLogger("skip");
	private SpanReporter spanReporter = new NoOpSpanReporter();
	private AlwaysSampler sampler = new AlwaysSampler();
	private Tracer tracer = new DefaultTracer(sampler, new Random(), spanNamer, spanLogger, spanReporter, traceKeys);
	private HttpTraceKeysInjector injector = new HttpTraceKeysInjector(tracer, traceKeys);
	
	/**
	 * Test that the correct tag values are set based on Http Headers 
	 * 
	 * @throws Exception
	 */
	@Test
	public void testHttpHeadersToTags() throws Exception {
		// Given
		Span span = tracer.createSpan("TestSpan", sampler);
		URL url = new URL("http://localhost:8080/");

		HttpHeaders headers = new HttpHeaders();
		headers.add("User-Agent", "Test");
		headers.put("Accept", Arrays.asList(MediaType.TEXT_PLAIN_VALUE, MediaType.TEXT_XML_VALUE));
		headers.add("Content-Length", "0");
		headers.add(Span.TRACE_ID_NAME,"3bb9a1fa9e70fdbd763261a53162f330");
		headers.add(Span.SPAN_ID_NAME, "763261a53162f330");
		headers.add(Span.SAMPLED_NAME,"1");
		headers.add(Span.SPAN_NAME_NAME, "http:/");

		traceKeys.getHttp().setHeaders(Arrays.asList("Accept", "User-Agent", "Content-Type"));;

		// when
		injector.addRequestTags(url.toString(), url.getHost(), url.getPath(), HttpMethod.GET.name(), headers );
		
		// verify
		then(span.tags())
			.containsEntry("http.user-agent", "Test")
			.containsEntry("http.accept", "'text/plain','text/xml'")
			.doesNotContainKey("http.content-type");
	}
}
