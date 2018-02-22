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

package org.springframework.cloud.sleuth.instrument.web.client.discoveryexception;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.sampler.Sampler;
import zipkin2.reporter.Reporter;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {
		WebClientDiscoveryExceptionTests.TestConfiguration.class }, webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = { "spring.application.name=exceptionservice",
		"spring.sleuth.http.legacy.enabled=true" })
@DirtiesContext
public class WebClientDiscoveryExceptionTests {

	@Autowired TestFeignInterfaceWithException testFeignInterfaceWithException;
	@Autowired @LoadBalanced RestTemplate template;
	@Autowired Tracer tracer;
	@Autowired ArrayListSpanReporter reporter;

	@Before
	public void close() {
		this.reporter.clear();
	}

	// issue #240
	private void shouldCloseSpanUponException(ResponseEntityProvider provider)
			throws IOException, InterruptedException {
		Span span = this.tracer.nextSpan().name("new trace");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			provider.get(this);
			Assertions.fail("should throw an exception");
		}
		catch (RuntimeException e) {
		}
		finally {
			span.finish();
		}

		// hystrix commands should finish at this point
		Thread.sleep(200);
		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(2);
		then(spans.stream()
				.filter(span1 -> span1.kind() == zipkin2.Span.Kind.CLIENT)
				.findFirst()
				.get().tags()).containsKey("error");
	}

	@Test
	public void testFeignInterfaceWithException() throws Exception {
		shouldCloseSpanUponException(
				(ResponseEntityProvider) (tests) -> tests.testFeignInterfaceWithException
						.shouldFailToConnect());
	}

	@Test
	public void testTemplate() throws Exception {
		shouldCloseSpanUponException((ResponseEntityProvider) (tests) -> tests.template
				.getForEntity("http://exceptionservice/", Map.class));
	}

	@FeignClient("exceptionservice")
	public interface TestFeignInterfaceWithException {
		@RequestMapping(method = RequestMethod.GET, value = "/")
		ResponseEntity<String> shouldFailToConnect();
	}

	@Configuration
	@EnableAutoConfiguration(exclude = {EurekaClientAutoConfiguration.class,
			TraceWebServletAutoConfiguration.class})
	@EnableDiscoveryClient
	@EnableFeignClients
	@RibbonClient("exceptionservice")
	public static class TestConfiguration {

		@LoadBalanced
		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean Reporter<zipkin2.Span> mySpanReporter() {
			return new ArrayListSpanReporter();
		}
	}

	@FunctionalInterface
	interface ResponseEntityProvider {
		ResponseEntity<?> get(
				WebClientDiscoveryExceptionTests webClientTests);
	}
}
