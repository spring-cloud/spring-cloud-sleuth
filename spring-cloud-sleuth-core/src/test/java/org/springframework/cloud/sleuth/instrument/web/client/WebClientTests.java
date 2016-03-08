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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.After;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.SleuthAssertions;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import static junitparams.JUnitParamsRunner.$;
import static org.assertj.core.api.BDDAssertions.then;

@RunWith(JUnitParamsRunner.class)
@SpringApplicationConfiguration(classes = { WebClientTests.TestConfiguration.class })
@WebIntegrationTest(value = { "spring.application.name=fooservice" }, randomPort = true)
public class WebClientTests {

	@ClassRule public static final SpringClassRule SCR = new SpringClassRule();
	@Rule public final SpringMethodRule springMethodRule = new SpringMethodRule();
	
	@Autowired TestFeignInterface testFeignInterface;
	@Autowired TestFeignInterfaceWithException testFeignInterfaceWithException;
	@Autowired @LoadBalanced RestTemplate template;
	@Autowired Listener listener;
	@Autowired Tracer tracer;

	@After
	public void close() {
		TestSpanContextHolder.removeCurrentSpan();
		this.listener.getEvents().clear();
	}

	@Test
	@Parameters
	@SuppressWarnings("unchecked")
	public void shouldCreateANewSpanWhenNoPreviousTracingWasPresent(ResponseEntityProvider provider) {
		ResponseEntity<String> response = provider.get(this);

		then(getHeader(response, Span.TRACE_ID_NAME)).isNotNull();
		then(getHeader(response, Span.SPAN_ID_NAME)).isNotNull();
		then(this.listener.getEvents()).isNotEmpty();
	}

	private Object[] parametersForShouldCreateANewSpanWhenNoPreviousTracingWasPresent() {
		return $((ResponseEntityProvider) (tests) -> tests.testFeignInterface.getNoTrace(),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/notrace", String.class));
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
		then(response.getBody().get(Span.NOT_SAMPLED_NAME)).isNotNull();
		then(this.listener.getEvents()).isNotEmpty();
	}

	private Object[] parametersForShouldPropagateNotSamplingHeader() {
		return $((ResponseEntityProvider) (tests) -> tests.testFeignInterface.headers(),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/", Map.class));
	}

	@Test
	@Parameters
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenCallingAnotherService(ResponseEntityProvider provider) {
		Long currentTraceId = 1L;
		Long currentParentId = 2L;
		Long currentSpanId = 100L;
		this.tracer.continueSpan(Span.builder().traceId(currentTraceId)
				.spanId(currentSpanId).parent(currentParentId).build());

		ResponseEntity<String> response = provider.get(this);

		then(Span.hexToId(getHeader(response, Span.TRACE_ID_NAME)))
				.isEqualTo(currentTraceId);
		thenRegisteredClientSentAndReceivedEvents();
	}

	private Object[] parametersForShouldAttachTraceIdWhenCallingAnotherService() {
		return $((ResponseEntityProvider) (tests) -> tests.testFeignInterface.headers(),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/traceid", String.class));
	}

	@Test
	@Parameters
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenUsingFeignClientWithoutResponseBody(ResponseEntityProvider provider) {
		Long currentTraceId = 1L;
		Long currentParentId = 2L;
		Long currentSpanId = generatedId();
		Span span = Span.builder().traceId(currentTraceId)
				.spanId(currentSpanId).parent(currentParentId).build();
		this.tracer.continueSpan(span);

		provider.get(this);

		thenRegisteredClientSentAndReceivedEvents();
		then(this.tracer.getCurrentSpan()).isEqualTo(span);
	}

	private Object[] parametersForShouldAttachTraceIdWhenUsingFeignClientWithoutResponseBody() {
		return $((ResponseEntityProvider) (tests) -> tests.testFeignInterface.noResponseBody(),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/noresponse", String.class));
	}

	// issue #198
	@Test
	@Parameters
	@SuppressWarnings("unchecked")
	public void shouldCloseSpanUponException(ResponseEntityProvider provider) throws IOException {
		Span span = this.tracer.createSpan("new trace");

		try {
			provider.get(this);
			Assert.fail("should throw an exception");
		} catch (RuntimeException e) {
			SleuthAssertions.then(e).hasRootCauseInstanceOf(IOException.class);
		}

		SleuthAssertions.then(this.tracer.getCurrentSpan()).isEqualTo(span);
		this.tracer.close(span);
	}

	private Object[] parametersForShouldCloseSpanUponException() {
		return $((ResponseEntityProvider) (tests) -> tests.testFeignInterfaceWithException.shouldFailToConnect(),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://exceptionService/", Map.class));
	}

	private void thenRegisteredClientSentAndReceivedEvents() {
		then(this.listener.getEvents().size()).isEqualTo(2);
		then(this.listener.getEvents().get(0)).isExactlyInstanceOf(ClientSentEvent.class);
		then(this.listener.getEvents().get(1)).isExactlyInstanceOf(ClientReceivedEvent.class);
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

	@FeignClient(name = "exceptionService", url = "http://invalid.host.to.break.tests")
	public interface TestFeignInterfaceWithException {
		@RequestMapping(method = RequestMethod.GET, value = "/")
		ResponseEntity<String> shouldFailToConnect();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableFeignClients
	@RibbonClients(defaultConfiguration = SimpleRibbonClientConfiguration.class)
	public static class TestConfiguration {

		@Bean
		FooController fooController() {
			return new FooController();
		}

		@Bean
		Listener listener() {
			return new Listener();
		}

		@LoadBalanced
		@Bean
		public RestTemplate restTemplate() {
				return new RestTemplate();
		}
	}

	@Component
	public static class Listener {
		private List<ApplicationEvent> events = new ArrayList<>();

		@EventListener(ClientSentEvent.class)
		public void sent(ClientSentEvent event) {
			this.events.add(event);
		}

		@EventListener(ClientReceivedEvent.class)
		public void received(ClientReceivedEvent event) {
			this.events.add(event);
		}

		public List<ApplicationEvent> getEvents() {
			return this.events;
		}
	}

	@RestController
	public static class FooController {

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
		ResponseEntity get(WebClientTests webClientTests);
	}
}
