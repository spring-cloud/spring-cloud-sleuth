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

package org.springframework.cloud.sleuth.instrument.web.client.exception;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;

@ContextConfiguration(classes = WebClientExceptionTests.TestConfiguration.class)
@TestPropertySource(properties = "spring.application.name=exceptionservice")
public class WebClientExceptionTests {

	private static final Log log = LogFactory.getLog(WebClientExceptionTests.class);

	@Autowired
	TestFeignInterfaceWithException testFeignInterfaceWithException;

	@Autowired
	@LoadBalanced
	RestTemplate template;

	@Autowired
	Tracer tracer;

	@Autowired
	TestSpanHandler spans;

	@BeforeEach
	public void open() {
		this.spans.clear();
	}

	// issue #198
	@ParameterizedTest
	@MethodSource("parametersForShouldCloseSpanUponException")
	@DirtiesContext
	public void shouldCloseSpanUponException(ResponseEntityProvider provider) throws IOException {
		Span span = this.tracer.nextSpan().name("new trace").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
			log.info("Started new span " + span);
			provider.get(this);
			fail("should throw an exception");
		}
		catch (RuntimeException e) {
			// SleuthAssertions.then(e).hasRootCauseInstanceOf(IOException.class);
		}
		finally {
			span.end();
		}

		then(this.tracer.currentSpan()).isNull();
		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(this.spans).isNotEmpty();
			log.info("Reported spans are not empty [" + this.spans + "]");
			then(this.spans.get(0).getError()).isNotNull();
		});
	}

	static Stream<Object> parametersForShouldCloseSpanUponException() {
		return Stream.of(
				(ResponseEntityProvider) (tests) -> tests.testFeignInterfaceWithException.shouldFailToConnect(),
				(ResponseEntityProvider) (tests) -> tests.template.getForEntity("https://exceptionservice/",
						Map.class));
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

	@Configuration(proxyBeanMethods = false)
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

	}

	@Configuration(proxyBeanMethods = false)
	public static class ExceptionServiceLoadBalancerClientConfiguration {

		@Bean
		public ServiceInstanceListSupplier serviceInstanceListSupplier(Environment env) {
			return ServiceInstanceListSupplier.fixed(env)
					.instance("invalid.host.to.break.tests", 1234, "exceptionservice").build();
		}

	}

}
