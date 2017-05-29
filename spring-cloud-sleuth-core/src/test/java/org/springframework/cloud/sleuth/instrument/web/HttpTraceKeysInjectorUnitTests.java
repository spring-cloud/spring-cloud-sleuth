package org.springframework.cloud.sleuth.instrument.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import org.junit.Before;
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


public class HttpTraceKeysInjectorUnitTests {
	TraceKeys traceKeys = new TraceKeys();
	private SpanNamer spanNamer = new DefaultSpanNamer();
	private SpanLogger spanLogger = new Slf4jSpanLogger("skip");
	private SpanReporter spanReporter = new NoOpSpanReporter();
	AlwaysSampler sampler = new AlwaysSampler();
	Tracer tracer = new DefaultTracer(sampler, new Random(), spanNamer, spanLogger, spanReporter, traceKeys);
	HttpTraceKeysInjector injector;
	
	@Before
	public void setup() {
		assertNotNull(tracer);
		injector = new HttpTraceKeysInjector(tracer, traceKeys);
	}
	
	@Test
	public void testHttpAttributes() throws Exception {
		traceKeys.getHttp().setHeaders(Arrays.asList("Accept", "User-Agent", "Content-Type"));;
		
		URL url = new URL("http://localhost:8080/");
		HttpHeaders headers = new HttpHeaders();
		headers.add("User-Agent", "Test");
		headers.put("Accept", Arrays.asList(MediaType.TEXT_PLAIN_VALUE, MediaType.TEXT_XML_VALUE));
		headers.add("Content-Length", "0");
		headers.add("X-B3-TraceId","3bb9a1fa9e70fdbd763261a53162f330");
		headers.add("X-B3-SpanId", "763261a53162f330");
		headers.add("X-B3-Sampled","1");
		headers.add("X-Span-Name", "http:/");
		
		Span span = tracer.createSpan("TestSpan", sampler);
		injector.addRequestTags(url.toString(), url.getHost(), url.getPath(), HttpMethod.GET.name(), headers );
		Map<String, String> tags = span.tags();
		assertEquals("Test", tags.get("http.user-agent"));
		assertEquals("'text/plain','text/xml'", tags.get("http.accept"));
		assertNull(tags.get("http.content-type"));

	}
}
