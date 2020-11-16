/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client.integration.sampled;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.UnknownHttpStatusCodeException;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;

@ContextConfiguration(classes = WebClientTests.TestConfiguration.class)
@TestPropertySource(properties = { "spring.sleuth.web.servlet.enabled=false", "spring.application.name=fooservice",
		"spring.sleuth.web.client.skip-pattern=/skip.*" })
@DirtiesContext
public abstract class WebClientTests {

	private static final Log log = LogFactory.getLog(WebClientTests.class);

	@Autowired
	TestFeignInterface testFeignInterface;

	@Autowired
	@LoadBalanced
	RestTemplate template;

	@Autowired
	WebClient webClient;

	@Autowired
	WebClient.Builder webClientBuilder;

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracer tracer;

	@Autowired
	TestErrorController testErrorController;

	@Autowired
	RestTemplateBuilder restTemplateBuilder;

	@LocalServerPort
	int port;

	@Autowired
	FooController fooController;

	@Autowired
	MyRestTemplateCustomizer customizer;

	@AfterEach
	@BeforeEach
	public void close() {
		this.spans.clear();
		this.testErrorController.clear();
		this.fooController.clear();
	}

	@ParameterizedTest
	@MethodSource("parametersForShouldCreateANewSpanWithClientSideTagsWhenNoPreviousTracingWasPresent")
	@SuppressWarnings("unchecked")
	public void shouldCreateANewSpanWithClientSideTagsWhenNoPreviousTracingWasPresent(ResponseEntityProvider provider) {
		ResponseEntity<String> response = provider.get(this);

		Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			then(getHeader(response, "b3")).isNull();
			then(this.spans).isNotEmpty();
			Optional<FinishedSpan> noTraceSpan = this.spans.reportedSpans().stream()
					.filter(span -> span.getName().contains("GET") && !span.getTags().isEmpty()
							&& span.getTags().containsKey("http.path"))
					.findFirst();
			then(noTraceSpan.isPresent()).isTrue();
			then(noTraceSpan.get().getTags()).containsEntry("http.path", "/notrace").containsEntry("http.method",
					"GET");
			// TODO: matches cause there is an issue with Feign not providing the full URL
			// at the interceptor level
			then(noTraceSpan.get().getTags().get("http.path")).matches(".*/notrace");
		});
		then(this.tracer.currentSpan()).isNull();
	}

	static Stream parametersForShouldCreateANewSpanWithClientSideTagsWhenNoPreviousTracingWasPresent() {
		return Stream.of((ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace",
						String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace",
						String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace",
						String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace",
						String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace",
						String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace",
						String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace",
						String.class),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace",
						String.class));
	}

	@ParameterizedTest
	@MethodSource("parametersForShouldAttachTraceIdWhenCallingAnotherService")
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenCallingAnotherService(ResponseEntityProvider provider) {
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
			ResponseEntity<String> response = provider.get(this);

			// https://github.com/spring-cloud/spring-cloud-sleuth/issues/327
			// we don't want to respond with any tracing data
			then(getHeader(response, "b3")).isNull();
		}
		finally {
			span.end();
		}

		then(this.tracer.currentSpan()).isNull();
		then(this.spans).isNotEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenCallingAnotherServiceViaWebClient() {
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
			this.webClient.get().uri("http://localhost:" + this.port + "/traceid").retrieve().bodyToMono(String.class)
					.block(Duration.ofMillis(100));
		}
		finally {
			span.end();
		}
		then(this.tracer.currentSpan()).isNull();
		then(this.spans.reportedSpans().stream().filter(r -> r.getKind() != null).map(r -> r.getKind().name())
				.collect(Collectors.toList())).isNotEmpty().contains("CLIENT");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldWorkWhenCustomStatusCodeIsReturned() {
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
			this.webClient.get().uri("http://localhost:" + this.port + "/issue1462").retrieve().bodyToMono(String.class)
					.block(Duration.ofSeconds(5));
		}
		catch (UnknownHttpStatusCodeException ex) {

		}
		finally {
			span.end();
		}

		then(this.tracer.currentSpan()).isNull();
		then(this.spans.reportedSpans().stream().filter(r -> r.getKind() != null).map(r -> r.getKind().name())
				.collect(Collectors.toList())).isNotEmpty().contains("CLIENT");
	}

	/**
	 * Cancel before {@link Subscription#request(long)} means a network request was never
	 * sent
	 */
	@Test
	@Disabled("flakey")
	public void shouldNotTagOnCancel() {
		this.webClient.get().uri("http://localhost:" + this.port + "/doNotSkip").retrieve().bodyToMono(String.class)
				.subscribe(new BaseSubscriber<String>() {
					@Override
					protected void hookOnSubscribe(Subscription subscription) {
						cancel();
					}
				});

		then(this.spans).isEmpty();
	}

	@Test
	public void shouldRespectSkipPattern() {
		this.webClient.get().uri("http://localhost:" + this.port + "/skip").retrieve().bodyToMono(String.class)
				.block(Duration.ofSeconds(5));
		then(this.spans).isEmpty();

		this.webClient.get().uri("http://localhost:" + this.port + "/doNotSkip").retrieve().bodyToMono(String.class)
				.block(Duration.ofSeconds(5));
		then(this.spans).isNotEmpty();
	}

	static Stream parametersForShouldAttachTraceIdWhenCallingAnotherService() {
		return Stream.of((ResponseEntityProvider) (tests) -> tests.testFeignInterface.headers(),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/traceid",
						String.class));
	}

	@ParameterizedTest
	@MethodSource("parametersForShouldAttachTraceIdWhenUsingFeignClientWithoutResponseBody")
	public void shouldAttachTraceIdWhenUsingFeignClientWithoutResponseBody(ResponseEntityProvider provider) {
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
			provider.get(this);
		}
		finally {
			span.end();
		}

		then(this.tracer.currentSpan()).isNull();
		then(this.spans).isNotEmpty();
	}

	static Stream parametersForShouldAttachTraceIdWhenUsingFeignClientWithoutResponseBody() {
		return Stream.of((ResponseEntityProvider) (tests) -> tests.testFeignInterface.noResponseBody(),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/noresponse",
						String.class));
	}

	@Test
	public void shouldCloseSpanWhenErrorControllerGetsCalled() {
		try {
			this.template.getForEntity("http://fooservice/nonExistent", String.class);
			fail("An exception should be thrown");
		}
		catch (HttpClientErrorException e) {
		}

		then(this.tracer.currentSpan()).isNull();
		Optional<FinishedSpan> storedSpan = this.spans.reportedSpans().stream()
				.filter(span -> "404".equals(span.getTags().get("http.status_code"))).findFirst();
		then(storedSpan.isPresent()).isTrue();
		this.spans.reportedSpans().stream().forEach(span -> {
			int initialSize = span.getEvents().size();
			int distinctSize = span.getEvents().stream().map(Map.Entry::getValue).distinct()
					.collect(Collectors.toList()).size();
			log.info("logs " + span.getEvents());
			then(initialSize).as("there are no duplicate log entries").isEqualTo(distinctSize);
		});

		then(this.spans.reportedSpans().stream().filter(r -> r.getKind() != null).map(r -> r.getKind().name())
				.collect(Collectors.toList())).isNotEmpty().contains("CLIENT");
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

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
			RestTemplate template = this.restTemplateBuilder.build();

			template.getForObject("http://localhost:" + this.port + "/traceid", String.class);
		}
		finally {
			span.end();
		}
		then(this.tracer.currentSpan()).isNull();
		then(this.customizer.isExecuted()).isTrue();
		then(this.spans).extracting("kind.name").contains("CLIENT");
	}

	@Test
	public void should_add_headers_eagerly() {
		Span span = this.tracer.nextSpan().name("foo").start();

		AtomicReference<String> traceId = new AtomicReference<>();
		try (Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
			this.webClientBuilder.filter((request, exchange) -> {
				traceId.set(request.headers().getFirst("b3"));

				return exchange.exchange(request);
			}).build().get().uri("http://localhost:" + this.port + "/traceid").retrieve().bodyToMono(String.class)
					.block(Duration.ofMillis(100));
		}
		finally {
			span.end();
		}
		then(traceId).doesNotHaveValue(null);
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

	@FunctionalInterface
	interface ResponseEntityProvider {

		@SuppressWarnings("rawtypes")
		ResponseEntity get(WebClientTests webClientTests);

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = JmxAutoConfiguration.class)
	@EnableFeignClients
	@LoadBalancerClient(value = "fooservice", configuration = SimpleLoadBalancerClientConfiguration.class)
	public static class TestConfiguration {

		@Bean
		FooController fooController() {
			return new FooController();
		}

		@Bean
		WebClientController webClientController() {
			return new WebClientController();
		}

		@LoadBalanced
		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		TestErrorController testErrorController(ErrorAttributes errorAttributes, Tracer tracer) {
			return new TestErrorController(errorAttributes, tracer);
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

	}

	static class MyRestTemplateCustomizer implements RestTemplateCustomizer {

		boolean executed;

		@Override
		public void customize(RestTemplate restTemplate) {
			this.executed = true;
		}

		public boolean isExecuted() {
			return this.executed;
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

		Span span;

		@RequestMapping(value = "/notrace", method = RequestMethod.GET)
		public String notrace(@RequestHeader(name = "b3", required = false) String b3Single) {
			then(b3Single).isNotNull();
			return "OK";
		}

		@RequestMapping(value = "/traceid", method = RequestMethod.GET)
		public String traceId(@RequestHeader("b3") String b3Single) {
			then(b3Single).isNotEmpty();
			return b3Single;
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
		public void noResponse(@RequestHeader("b3") String b3Single) {
			then(b3Single).isNotEmpty();
		}

		public Span getSpan() {
			return this.span;
		}

		public void clear() {
			this.span = null;
		}

	}

	@RestController
	public static class WebClientController {

		@RequestMapping(value = "/issue1462", method = RequestMethod.GET)
		public ResponseEntity<String> issue1462() {
			return ResponseEntity.status(499).body("issue1462");
		}

		@RequestMapping(value = { "/skip", "/doNotSkip" }, method = RequestMethod.GET)
		String skip() {
			return "ok";
		}

	}

	@Configuration(proxyBeanMethods = false)
	public static class SimpleLoadBalancerClientConfiguration {

		@Value("${local.server.port}")
		private int port = 0;

		@Bean
		public ServiceInstanceListSupplier serviceInstanceListSupplier(Environment env) {
			return ServiceInstanceListSupplier.fixed(env).instance(this.port, "fooservice").build();
		}

	}

}
