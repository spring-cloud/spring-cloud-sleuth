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

package org.springframework.cloud.sleuth.brave.instrument.web.client.discoveryexception;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import brave.Span;
import brave.Tracer;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = { WebClientDiscoveryExceptionTests.TestConfiguration.class }, webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = { "spring.application.name=exceptionservice" })
@DirtiesContext
public class WebClientDiscoveryExceptionTests {

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
	public void close() {
		this.spans.clear();
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
		then(this.spans.spans().stream().filter(span1 -> span1.kind() == Span.Kind.CLIENT).findFirst().get().error())
				.isNotNull();
	}

	@Test
	public void testFeignInterfaceWithException() throws Exception {
		shouldCloseSpanUponException(
				(ResponseEntityProvider) (tests) -> tests.testFeignInterfaceWithException.shouldFailToConnect());
	}

	@Test
	public void testTemplate() throws Exception {
		shouldCloseSpanUponException((ResponseEntityProvider) (tests) -> tests.template
				.getForEntity("https://exceptionservice/", Map.class));
	}

	@FeignClient("exceptionservice")
	public interface TestFeignInterfaceWithException {

		@RequestMapping(method = RequestMethod.GET, value = "/")
		ResponseEntity<String> shouldFailToConnect();

	}

	@FunctionalInterface
	interface ResponseEntityProvider {

		ResponseEntity<?> get(WebClientDiscoveryExceptionTests webClientTests);

	}

	@Configuration
	@EnableAutoConfiguration(
			excludeName = "org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration",
			exclude = EurekaClientAutoConfiguration.class)
	@EnableDiscoveryClient
	@EnableFeignClients
	@LoadBalancerClient("exceptionservice")
	public static class TestConfiguration {

		@LoadBalanced
		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		@Bean
		ServiceInstanceListSupplier serviceInstanceListSupplier() {
			return new ServiceInstanceListSupplier() {
				@Override
				public String getServiceId() {
					return "exceptionservice";
				}

				@Override
				public Flux<List<ServiceInstance>> get() {
					return Flux.just(Collections.singletonList(new ServiceInstance() {
						@Override
						public String getServiceId() {
							return "exceptionservice";
						}

						@Override
						public String getHost() {
							return "localhost";
						}

						@Override
						public int getPort() {
							return 1234;
						}

						@Override
						public boolean isSecure() {
							return false;
						}

						@Override
						public URI getUri() {
							return null;
						}

						@Override
						public Map<String, String> getMetadata() {
							return null;
						}
					}));
				}
			};
		}

	}

}
