/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client.exception;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(classes = { WebClientExceptionTests.TestConfiguration.class },
		properties = { "spring.application.name=exceptionservice" },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebClientExceptionTests {

	private static final Log log = LogFactory.getLog(WebClientExceptionTests.class);

	@Autowired
	TestFeignInterfaceWithException testFeignInterfaceWithException;

	@Autowired
	@LoadBalanced
	RestTemplate template;

	@Autowired
	Tracing tracer;

	@Autowired
	TestSpanHandler spans;

	@BeforeEach
	public void open() {
		this.spans.clear();
	}

	// issue #198
	@ParameterizedTest
	@MethodSource("parametersForShouldCloseSpanUponException")
	public void shouldCloseSpanUponException(ResponseEntityProvider provider)
			throws IOException {
		Span span = this.tracer.tracer().nextSpan().name("new trace").start();

		try (Tracer.SpanInScope ws = this.tracer.tracer().withSpanInScope(span)) {
			log.info("Started new span " + span);
			provider.get(this);
			fail("should throw an exception");
		}
		catch (RuntimeException e) {
			// SleuthAssertions.then(e).hasRootCauseInstanceOf(IOException.class);
		}
		finally {
			span.finish();
		}

		then(this.tracer.tracer().currentSpan()).isNull();
		then(this.spans).isNotEmpty();
		then(this.spans.get(0).error()).isNotNull();
	}

	static Stream<Object> parametersForShouldCloseSpanUponException() {
		return Stream.of(
				(ResponseEntityProvider) (tests) -> tests.testFeignInterfaceWithException
						.shouldFailToConnect(),
				(ResponseEntityProvider) (tests) -> tests.template
						.getForEntity("https://exceptionservice/", Map.class));
	}

	@FeignClient("exceptionservice")
	public interface TestFeignInterfaceWithException {

		@RequestMapping(method = RequestMethod.GET, value = "/")
		ResponseEntity<String> shouldFailToConnect();

	}

	@FunctionalInterface
	interface ResponseEntityProvider {

		ResponseEntity<?> get(WebClientExceptionTests webClientTests);

	}

	@Configuration
	@EnableAutoConfiguration
	@EnableFeignClients
	@LoadBalancerClient(value = "exceptionservice",
			configuration = ExceptionServiceLoadBalancerClientConfiguration.class)
	public static class TestConfiguration {

		@LoadBalanced
		@Bean
		public RestTemplate restTemplate() {
			SimpleClientHttpRequestFactory clientHttpRequestFactory = new SimpleClientHttpRequestFactory();
			clientHttpRequestFactory.setReadTimeout(1);
			clientHttpRequestFactory.setConnectTimeout(1);
			return new RestTemplate(clientHttpRequestFactory);
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

	}

	@Configuration
	public static class ExceptionServiceLoadBalancerClientConfiguration {

		@Bean
		public ServiceInstanceListSupplier serviceInstanceListSupplier(Environment env) {
			return ServiceInstanceListSupplier.fixed(env)
					.instance("invalid.host.to.break.tests", 1234, "exceptionservice")
					.build();
		}

	}

}
