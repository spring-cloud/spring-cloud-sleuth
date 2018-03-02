/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client.integration;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Hooks;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientResponse;
import zipkin2.Annotation;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;

@RunWith(JUnitParamsRunner.class)
@SpringBootTest(classes = WebClientTests.TestConfiguration.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
		"spring.sleuth.http.legacy.enabled=true",
		"spring.application.name=fooservice",
		"feign.hystrix.enabled=false" })
@DirtiesContext
public class WebClientTests {
	static final String TRACE_ID_NAME = "X-B3-TraceId";
	static final String SPAN_ID_NAME = "X-B3-SpanId";
	static final String SAMPLED_NAME = "X-B3-Sampled";
	static final String PARENT_ID_NAME = "X-B3-ParentSpanId";

	private static final org.apache.commons.logging.Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	@ClassRule public static final SpringClassRule SCR = new SpringClassRule();
	@Rule public final SpringMethodRule springMethodRule = new SpringMethodRule();

	@Autowired TestFeignInterface testFeignInterface;
	@Autowired @LoadBalanced RestTemplate template;
	@Autowired WebClient webClient;
	@Autowired WebClient.Builder webClientBuilder;
	@Autowired HttpClientBuilder httpClientBuilder; // #845
	@Autowired HttpClient nettyHttpClient;
	@Autowired HttpAsyncClientBuilder httpAsyncClientBuilder; // #845
	@Autowired ArrayListSpanReporter reporter;
	@Autowired Tracer tracer;
	@Autowired TestErrorController testErrorController;
	@Autowired RestTemplateBuilder restTemplateBuilder;
	@LocalServerPort int port;
	@Autowired FooController fooController;
	@Autowired MyRestTemplateCustomizer customizer;

	@After
	public void close() {
		this.reporter.clear();
		this.testErrorController.clear();
		this.fooController.clear();
	}

	@BeforeClass
	public static void cleanup() {
		Hooks.resetOnLastOperator();
		Schedulers.resetFactory();
	}

	@Test
	@Parameters
	@SuppressWarnings("unchecked")
	public void shouldCreateANewSpanWithClientSideTagsWhenNoPreviousTracingWasPresent(
			ResponseEntityProvider provider) {
		ResponseEntity<String> response = provider.get(this);

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			then(getHeader(response, TRACE_ID_NAME)).isNull();
			then(getHeader(response, SPAN_ID_NAME)).isNull();
			List<zipkin2.Span> spans = this.reporter.getSpans();
			then(spans).isNotEmpty();
			Optional<zipkin2.Span> noTraceSpan = new ArrayList<>(spans).stream()
					.filter(span -> "http:/notrace".equals(span.name()) && !span.tags()
							.isEmpty() && span.tags().containsKey("http.path")).findFirst();
			then(noTraceSpan.isPresent()).isTrue();
			then(noTraceSpan.get().tags())
					.containsEntry("http.path", "/notrace")
					.containsEntry("http.method", "GET");
			// TODO: matches cause there is an issue with Feign not providing the full URL at the interceptor level
			then(noTraceSpan.get().tags().get("http.url")).matches(".*/notrace");
		});
		then(Tracing.current().tracer().currentSpan()).isNull();
	}

	Object[] parametersForShouldCreateANewSpanWithClientSideTagsWhenNoPreviousTracingWasPresent() {
		return new Object[] {
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace", String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace", String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace", String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace", String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace", String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace", String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace", String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace", String.class)
		};
	}

	@Test
	@Parameters
	@SuppressWarnings("unchecked")
	public void shouldPropagateNotSamplingHeader(ResponseEntityProvider provider) {
		Span span = this.tracer.nextSpan(
				TraceContextOrSamplingFlags.create(SamplingFlags.NOT_SAMPLED))
				.name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			ResponseEntity<Map<String, String>> response = provider.get(this);

			then(response.getBody().get(TRACE_ID_NAME.toLowerCase())).isNotNull();
			then(response.getBody().get(SAMPLED_NAME.toLowerCase())).isEqualTo("0");
		} finally {
			span.finish();
		}

		then(this.reporter.getSpans()).isEmpty();
		then(Tracing.current().tracer().currentSpan()).isNull();
	}

	Object[] parametersForShouldPropagateNotSamplingHeader() throws Exception {
		return new Object[] {
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.headers(),
				(ResponseEntityProvider) (tests) -> tests.template
						.getForEntity("http://fooservice/", Map.class) };
	}

	@Test
	@Parameters
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenCallingAnotherService(
			ResponseEntityProvider provider) {
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			ResponseEntity<String> response = provider.get(this);

			// https://github.com/spring-cloud/spring-cloud-sleuth/issues/327
			// we don't want to respond with any tracing data
			then(getHeader(response, SAMPLED_NAME)).isNull();
			then(getHeader(response, TRACE_ID_NAME)).isNull();
		} finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
		then(this.reporter.getSpans()).isNotEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenCallingAnotherServiceForNettyHttpClient() throws Exception {
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			HttpClientResponse response = this.nettyHttpClient
					.get("http://localhost:" + port).block();

			then(response).isNotNull();
		}

		then(this.tracer.currentSpan()).isNull();
		then(this.reporter.getSpans())
				.isNotEmpty()
				.extracting("traceId", String.class)
				.containsOnly(span.context().traceIdString());
		then(this.reporter.getSpans())
				.extracting("kind.name")
				.contains("CLIENT");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenCallingAnotherServiceForHttpClient() throws Exception {
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			String response = this.httpClientBuilder.build()
					.execute(new HttpGet("http://localhost:" + port),
							new BasicResponseHandler());

			then(response).isNotEmpty();
		}

		then(this.tracer.currentSpan()).isNull();
		then(this.reporter.getSpans())
				.isNotEmpty()
				.extracting("traceId", String.class)
				.containsOnly(span.context().traceIdString());
		then(this.reporter.getSpans())
				.extracting("kind.name")
				.contains("CLIENT");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenCallingAnotherServiceForAsyncHttpClient() throws Exception {
		Span span = this.tracer.nextSpan().name("foo").start();

		CloseableHttpAsyncClient client = this.httpAsyncClientBuilder.build();
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			client.start();
			Future<HttpResponse> future = client
					.execute(new HttpGet("http://localhost:" + port),
							new FutureCallback<HttpResponse>() {
								@Override public void completed(HttpResponse result) {

								}

								@Override public void failed(Exception ex) {

								}

								@Override public void cancelled() {

								}
							});
			then(future.get()).isNotNull();
		} finally {
			client.close();
		}

		then(this.tracer.currentSpan()).isNull();
		then(this.reporter.getSpans())
				.isNotEmpty()
				.extracting("traceId", String.class)
				.containsOnly(span.context().traceIdString());
		then(this.reporter.getSpans())
				.extracting("kind.name")
				.contains("CLIENT");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenCallingAnotherServiceViaWebClient() {
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			this.webClient.get()
					.uri("http://localhost:" + this.port + "/traceid")
					.retrieve()
					.bodyToMono(String.class)
					.block();
		} finally {
			span.finish();
		}
		then(this.tracer.currentSpan()).isNull();
		then(this.reporter.getSpans())
				.isNotEmpty()
				.extracting("kind.name")
				.contains("CLIENT");
	}

	Object[] parametersForShouldAttachTraceIdWhenCallingAnotherService() {
		return new Object[] {
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.headers(),
				(ResponseEntityProvider) (tests) -> tests.template
						.getForEntity("http://fooservice/traceid", String.class) };
	}

	@Test
	@Parameters
	public void shouldAttachTraceIdWhenUsingFeignClientWithoutResponseBody(
			ResponseEntityProvider provider) {
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			provider.get(this);
		} finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
		then(this.reporter.getSpans()).isNotEmpty();
	}

	Object[] parametersForShouldAttachTraceIdWhenUsingFeignClientWithoutResponseBody() {
		return new Object[] {
				(ResponseEntityProvider) (tests) ->
						tests.testFeignInterface.noResponseBody(),
				(ResponseEntityProvider) (tests) ->
						tests.template.getForEntity("http://fooservice/noresponse", String.class)
		};
	}

	@Test
	public void shouldCloseSpanWhenErrorControllerGetsCalled() {
		try {
			this.template.getForEntity("http://fooservice/nonExistent", String.class);
			fail("An exception should be thrown");
		} catch (HttpClientErrorException e) { }

		then(this.tracer.currentSpan()).isNull();
		Optional<zipkin2.Span> storedSpan = this.reporter.getSpans().stream()
				.filter(span -> "404".equals(span.tags().get("http.status_code"))).findFirst();
		then(storedSpan.isPresent()).isTrue();
		List<zipkin2.Span> spans = this.reporter.getSpans();
		spans.stream()
				.forEach(span -> {
					int initialSize = span.annotations().size();
					int distinctSize = span.annotations().stream().map(Annotation::value).distinct()
							.collect(Collectors.toList()).size();
					log.info("logs " + span.annotations());
					then(initialSize).as("there are no duplicate log entries").isEqualTo(distinctSize);
				});

		then(this.reporter.getSpans())
				.isNotEmpty()
				.extracting("kind.name")
				.contains("CLIENT");
	}

	@Test
	public void shouldNotExecuteErrorControllerWhenUrlIsFound() {
		this.template.getForEntity("http://fooservice/notrace", String.class);

		then(this.tracer.currentSpan()).isNull();
		then(this.testErrorController.getSpan()).isNull();
	}

	@Test
	public void should_wrap_rest_template_builders() {
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			RestTemplate template = this.restTemplateBuilder.build();

			template.getForObject("http://localhost:" + this.port + "/traceid", String.class);
		} finally {
			span.finish();
		}
		then(this.tracer.currentSpan()).isNull();
		then(this.customizer.isExecuted()).isTrue();
		then(this.reporter.getSpans())
				.extracting("kind.name")
				.contains("CLIENT");
	}

	private String getHeader(ResponseEntity<String> response, String name) {
		List<String> headers = response.getHeaders().get(name);
		return headers == null || headers.isEmpty() ? null : headers.get(0);
	}

	@FeignClient("fooservice")
	public interface TestFeignInterface {
		@RequestMapping(method = RequestMethod.GET, value = "/traceid")
		ResponseEntity<String> getTraceId();

		@RequestMapping(method = RequestMethod.GET, value = "/notrace")
		ResponseEntity<String> getNoTrace();

		@RequestMapping(method = RequestMethod.GET, value = "/")
		ResponseEntity<Map<String, String>> headers();

		@RequestMapping(method = RequestMethod.GET, value = "/noresponse")
		ResponseEntity<Void> noResponseBody();
	}

	@Configuration
	@EnableAutoConfiguration(exclude = TraceWebServletAutoConfiguration.class)
	@EnableFeignClients
	@RibbonClient(value = "fooservice", configuration = SimpleRibbonClientConfiguration.class)
	public static class TestConfiguration {

		@Bean
		FooController fooController() {
			return new FooController();
		}

		@LoadBalanced
		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean Sampler testSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		TestErrorController testErrorController(ErrorAttributes errorAttributes, Tracing tracer) {
			return new TestErrorController(errorAttributes, tracer.tracer());
		}

		@Bean Reporter<zipkin2.Span> spanReporter() {
			return new ArrayListSpanReporter();
		}

		@Bean
		WebClient webClient() {
			return WebClient.builder().build();
		}

		@Bean
		WebClient.Builder webClientBuilder() {
			return WebClient.builder();
		}

		@Bean
		RestTemplateCustomizer myRestTemplateCustomizer() {
			return new MyRestTemplateCustomizer();
		}

		@Bean HttpClient reactorHttpClient() {
			return HttpClient.create();
		}
	}

	static class MyRestTemplateCustomizer implements RestTemplateCustomizer {
		boolean executed;

		@Override public void customize(RestTemplate restTemplate) {
			this.executed = true;
		}

		public boolean isExecuted() {
			return executed;
		}
	}

	public static class TestErrorController extends BasicErrorController {

		private final Tracer tracer;

		Span span;

		public TestErrorController(ErrorAttributes errorAttributes, Tracer tracer) {
			super(errorAttributes, new ServerProperties().getError());
			this.tracer = tracer;
		}

		@Override
		public ResponseEntity<Map<String, Object>> error(HttpServletRequest request) {
			this.span = this.tracer.currentSpan();
			return super.error(request);
		}

		public Span getSpan() {
			return this.span;
		}

		public void clear() {
			this.span = null;
		}
	}

	@RestController
	public static class FooController {

		@Autowired
		Tracer tracer;

		Span span;

		@RequestMapping(value = "/notrace", method = RequestMethod.GET)
		public String notrace(
				@RequestHeader(name = TRACE_ID_NAME, required = false) String traceId) {
			then(traceId).isNotNull();
			return "OK";
		}

		@RequestMapping(value = "/traceid", method = RequestMethod.GET)
		public String traceId(@RequestHeader(TRACE_ID_NAME) String traceId,
				@RequestHeader(SPAN_ID_NAME) String spanId,
				@RequestHeader(PARENT_ID_NAME) String parentId) {
			then(traceId).isNotEmpty();
			then(parentId).isNotEmpty();
			then(spanId).isNotEmpty();
			this.span = this.tracer.currentSpan();
			return traceId;
		}

		@RequestMapping("/")
		public Map<String, String> home(@RequestHeader HttpHeaders headers) {
			Map<String, String> map = new HashMap<>();
			for (String key : headers.keySet()) {
				map.put(key, headers.getFirst(key));
			}
			return map;
		}

		@RequestMapping("/noresponse")
		public void noResponse(@RequestHeader(TRACE_ID_NAME) String traceId,
				@RequestHeader(SPAN_ID_NAME) String spanId,
				@RequestHeader(PARENT_ID_NAME) String parentId) {
			then(traceId).isNotEmpty();
			then(parentId).isNotEmpty();
			then(spanId).isNotEmpty();
		}

		public Span getSpan() {
			return this.span;
		}

		public void clear() {
			this.span = null;
		}
	}

	@Configuration
	public static class SimpleRibbonClientConfiguration {

		@Value("${local.server.port}")
		private int port = 0;

		@Bean
		public ILoadBalancer ribbonLoadBalancer() {
			BaseLoadBalancer balancer = new BaseLoadBalancer();
			balancer.setServersList(
					Collections.singletonList(new Server("localhost", this.port)));
			return balancer;
		}
	}

	@FunctionalInterface
	interface ResponseEntityProvider {
		@SuppressWarnings("rawtypes")
		ResponseEntity get(
				WebClientTests webClientTests);
	}
}