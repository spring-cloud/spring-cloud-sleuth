/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.BasicErrorController;
import org.springframework.boot.autoconfigure.web.ErrorAttributes;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.sleuth.Log;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import static junitparams.JUnitParamsRunner.$;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(JUnitParamsRunner.class)
@SpringApplicationConfiguration(classes = { WebClientTests.TestConfiguration.class })
@WebIntegrationTest(value = { "spring.application.name=fooservice" }, randomPort = true)
public class WebClientTests {

	@ClassRule public static final SpringClassRule SCR = new SpringClassRule();
	@Rule public final SpringMethodRule springMethodRule = new SpringMethodRule();

	@Autowired TestFeignInterface testFeignInterface;
	@Autowired @LoadBalanced RestTemplate template;
	@Autowired ArrayListSpanAccumulator listener;
	@Autowired Tracer tracer;
	@Autowired TestErrorController testErrorController;

	@After
	public void close() {
		TestSpanContextHolder.removeCurrentSpan();
		this.listener.getSpans().clear();
		this.testErrorController.clear();
	}

	@Test
	@Parameters
	@SuppressWarnings("unchecked")
	public void shouldCreateANewSpanWithClientSideTagsWhenNoPreviousTracingWasPresent(
			ResponseEntityProvider provider) {
		ResponseEntity<String> response = provider.get(this);

		then(getHeader(response, Span.TRACE_ID_NAME)).isNotNull();
		then(getHeader(response, Span.SPAN_ID_NAME)).isNotNull();
		then(this.listener.getSpans()).isNotEmpty();
		Optional<Span> noTraceSpan = this.listener.getSpans().stream().filter(span ->
				"http:/notrace".equals(span.getName()) && !span.tags().isEmpty()).findFirst();
		then(noTraceSpan.isPresent()).isTrue();
		// TODO: matches cause there is an issue with Feign not providing the full URL at the interceptor level
		then(noTraceSpan.get()).matchesATag("http.url", ".*/notrace")
				.hasATag("http.path", "/notrace")
				.hasATag("http.method", "GET");
	}

	Object[] parametersForShouldCreateANewSpanWithClientSideTagsWhenNoPreviousTracingWasPresent() {
		return $(
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.template
						.getForEntity("http://fooservice/notrace", String.class));
	}

	@Test
	@Parameters
	@SuppressWarnings("unchecked")
	public void shouldPropagateNotSamplingHeader(ResponseEntityProvider provider) {
		Long currentTraceId = 1L;
		Long currentParentId = 2L;
		this.tracer.continueSpan(Span.builder().traceId(currentTraceId)
				.spanId(generatedId()).exportable(false).parent(currentParentId).build());

		ResponseEntity<Map<String, String>> response = provider.get(this);

		then(response.getBody().get(Span.TRACE_ID_NAME)).isNotNull();
		then(response.getBody().get(Span.SAMPLED_NAME)).isEqualTo(Span.SPAN_NOT_SAMPLED);
		then(this.listener.getSpans()).isNotEmpty();
	}

	Object[] parametersForShouldPropagateNotSamplingHeader() {
		return $((ResponseEntityProvider) (tests) -> tests.testFeignInterface.headers(),
				(ResponseEntityProvider) (tests) -> tests.template
						.getForEntity("http://fooservice/", Map.class));
	}

	@Test
	@Parameters
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenCallingAnotherService(
			ResponseEntityProvider provider) {
		Long currentTraceId = 1L;
		Long currentParentId = 2L;
		Long currentSpanId = 100L;
		this.tracer.continueSpan(Span.builder().traceId(currentTraceId)
				.spanId(currentSpanId).parent(currentParentId).build());

		ResponseEntity<String> response = provider.get(this);

		then(getHeader(response, Span.SAMPLED_NAME)).isEqualTo(Span.SPAN_SAMPLED);
		then(Span.hexToId(getHeader(response, Span.TRACE_ID_NAME)))
				.isEqualTo(currentTraceId);
		thenRegisteredClientSentAndReceivedEvents(spanWithClientEvents());
	}

	private Span spanWithClientEvents() {
		return this.listener.getSpans().stream()
				.filter(span -> span.logs().stream()
						.filter(log -> log.getEvent().contains(Span.CLIENT_RECV)
								|| log.getEvent().contains(Span.CLIENT_SEND))
						.findFirst().isPresent())
				.findFirst().get();
	}

	Object[] parametersForShouldAttachTraceIdWhenCallingAnotherService() {
		return $((ResponseEntityProvider) (tests) -> tests.testFeignInterface.headers(),
				(ResponseEntityProvider) (tests) -> tests.template
						.getForEntity("http://fooservice/traceid", String.class));
	}

	@Test
	@Parameters
	public void shouldAttachTraceIdWhenUsingFeignClientWithoutResponseBody(
			ResponseEntityProvider provider) {
		Long currentTraceId = 1L;
		Long currentParentId = 2L;
		Long currentSpanId = generatedId();
		Span span = Span.builder().traceId(currentTraceId).spanId(currentSpanId)
				.parent(currentParentId).build();
		this.tracer.continueSpan(span);

		provider.get(this);

		then(this.tracer.getCurrentSpan()).isEqualTo(span);
		thenRegisteredClientSentAndReceivedEvents(spanWithClientEvents());
	}

	Object[] parametersForShouldAttachTraceIdWhenUsingFeignClientWithoutResponseBody() {
		return $(
				(ResponseEntityProvider) (tests) -> tests.testFeignInterface
						.noResponseBody(),
				(ResponseEntityProvider) (tests) -> tests.template
						.getForEntity("http://fooservice/noresponse", String.class));
	}

	@Test
	public void shouldCloseSpanWhenErrorControllerGetsCalled() {
		try {
			this.template.getForEntity("http://fooservice/nonExistent", String.class);
			fail("An exception should be thrown");
		} catch (HttpClientErrorException e) { }

		then(this.tracer.getCurrentSpan()).isNull();
		Optional<Span> storedSpan = this.listener.getSpans().stream()
				.filter(span -> "404".equals(span.tags().get("http.status_code"))).findFirst();
		then(storedSpan.isPresent()).isTrue();
		this.listener.getSpans().stream()
				.forEach(span -> {
					int initialSize = span.logs().size();
					int distinctSize = span.logs().stream().map(Log::getEvent).distinct().collect(Collectors.toList()).size();
					then(initialSize).as("there are no duplicate log entries").isEqualTo(distinctSize);
				});
		then(this.testErrorController.getSpan()).isNotNull();
	}

	@Test
	public void shouldNotExecuteErrorControllerWhenUrlIsFound() {
		this.template.getForEntity("http://fooservice/notrace", String.class);

		then(this.tracer.getCurrentSpan()).isNull();
		then(this.testErrorController.getSpan()).isNull();
	}

	private void thenRegisteredClientSentAndReceivedEvents(Span span) {
		then(span).hasLoggedAnEvent(Span.CLIENT_RECV);
		then(span).hasLoggedAnEvent(Span.CLIENT_SEND);
	}

	private Long generatedId() {
		return new Random().nextLong();
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
	@EnableAutoConfiguration
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

		@Bean
		Sampler testSampler() {
			return new AlwaysSampler();
		}

		@Bean
		TestErrorController testErrorController(ErrorAttributes errorAttributes, Tracer tracer) {
			return new TestErrorController(errorAttributes, tracer);
		}

		@Bean
		SpanReporter spanReporter() {
			return new ArrayListSpanAccumulator();
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
			this.span = this.tracer.getCurrentSpan();
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

		@RequestMapping(value = "/notrace", method = RequestMethod.GET)
		public String notrace(
				@RequestHeader(name = Span.TRACE_ID_NAME, required = false) String traceId) {
			then(traceId).isNotNull();
			return "OK";
		}

		@RequestMapping(value = "/traceid", method = RequestMethod.GET)
		public String traceId(@RequestHeader(Span.TRACE_ID_NAME) String traceId,
				@RequestHeader(Span.SPAN_ID_NAME) String spanId,
				@RequestHeader(Span.PARENT_ID_NAME) String parentId) {
			then(traceId).isNotEmpty();
			then(parentId).isNotEmpty();
			then(spanId).isNotEmpty();
			return traceId;
		}

		@RequestMapping("/")
		public Map<String, String> home(@RequestHeader HttpHeaders headers) {
			Map<String, String> map = new HashMap<String, String>();
			for (String key : headers.keySet()) {
				for (String spanKey : Span.SPAN_HEADERS)
					if (key.equalsIgnoreCase(spanKey)) {
						key = spanKey;
					}
				map.put(key, headers.getFirst(key));
			}
			return map;
		}

		@RequestMapping("/noresponse")
		public void noResponse(@RequestHeader(Span.TRACE_ID_NAME) String traceId,
				@RequestHeader(Span.SPAN_ID_NAME) String spanId,
				@RequestHeader(Span.PARENT_ID_NAME) String parentId) {
			then(traceId).isNotEmpty();
			then(parentId).isNotEmpty();
			then(spanId).isNotEmpty();
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
		ResponseEntity get(WebClientTests webClientTests);
	}
}
