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

package org.springframework.cloud.sleuth.instrument.web.client.integration.notsampled;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
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
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

@ContextConfiguration(classes = WebClientNotSampledTests.TestConfiguration.class)
@TestPropertySource(properties = { "spring.sleuth.web.servlet.enabled=false", "spring.application.name=fooservice",
		"spring.sleuth.web.client.skip-pattern=/skip.*" })
@DirtiesContext
public abstract class WebClientNotSampledTests {

	@Autowired
	TestFeignInterface testFeignInterface;

	@Autowired
	@LoadBalanced
	RestTemplate template;

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracer tracer;

	@LocalServerPort
	int port;

	@Autowired
	FooController fooController;

	@AfterEach
	@BeforeEach
	public void close() {
		this.spans.clear();
		this.fooController.clear();
	}

	@ParameterizedTest
	@MethodSource("parametersForShouldPropagateNotSamplingHeader")
	@SuppressWarnings("unchecked")
	public void shouldPropagateNotSamplingHeader(ResponseEntityProvider provider) {
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
			ResponseEntity<Map<String, String>> response = provider.get(this);

			assertB3SingleNotSampled(response);
		}
		finally {
			span.end();
		}

		then(this.spans).isEmpty();
		then(this.tracer.currentSpan()).isNull();
	}

	public void assertB3SingleNotSampled(ResponseEntity<Map<String, String>> response) {
		throw new UnsupportedOperationException("Implement this assertion");
	}

	static Stream parametersForShouldPropagateNotSamplingHeader() throws Exception {
		return Stream.of((ResponseEntityProvider) (tests) -> tests.testFeignInterface.headers(),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("http://fooservice/", Map.class));
	}

	@FeignClient("fooservice")
	public interface TestFeignInterface {

		@RequestMapping(method = RequestMethod.GET, value = "/")
		ResponseEntity<Map<String, String>> headers();

	}

	@FunctionalInterface
	interface ResponseEntityProvider {

		@SuppressWarnings("rawtypes")
		ResponseEntity get(WebClientNotSampledTests webClientTests);

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

		@LoadBalanced
		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

	}

	@RestController
	public static class FooController {

		Span span;

		@RequestMapping("/")
		public Map<String, String> home(@RequestHeader HttpHeaders headers) {
			Map<String, String> map = new HashMap<>();
			for (String key : headers.keySet()) {
				map.put(key, headers.getFirst(key));
			}
			return map;
		}

		public Span getSpan() {
			return this.span;
		}

		public void clear() {
			this.span = null;
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
