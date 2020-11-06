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

package org.springframework.cloud.sleuth.instrument.web.client.integration.parser;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpRequestParser;
import org.springframework.cloud.sleuth.api.http.HttpResponseParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientResponseParser;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.BDDAssertions.then;

@ContextConfiguration(classes = WebClientCustomParserTests.TestConfiguration.class)
@TestPropertySource(
		properties = { "spring.application.name=fooservice", "spring.sleuth.web.client.skip-pattern=/skip.*" })
@DirtiesContext
public abstract class WebClientCustomParserTests {

	@Autowired
	TestFeignInterface testFeignInterface;

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

	@Test
	public void should_set_tags_via_server_and_client_parsers() {
		this.testFeignInterface.getTraceId();
		Map<String, String> clientSideTags = spans.reportedSpans().stream()
				.filter(f -> f.getKind().equals(Span.Kind.CLIENT)).flatMap(f -> f.getTags().entrySet().stream())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		then(clientSideTags).containsEntry("ClientRequest", "Tag").containsEntry("ClientRequestFeign", "GET")
				.containsEntry("ClientResponse", "Tag").containsEntry("ClientResponseFeign", "200");
	}

	@FeignClient("fooservice")
	public interface TestFeignInterface {

		@RequestMapping(method = RequestMethod.GET, value = "/traceid")
		ResponseEntity<String> getTraceId();

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { TraceWebServletAutoConfiguration.class, JmxAutoConfiguration.class })
	@EnableFeignClients
	@LoadBalancerClient(value = "fooservice", configuration = SimpleLoadBalancerClientConfiguration.class)
	@Import(ClientParserConfiguration.class)
	public static class TestConfiguration {

		@Bean
		FooController fooController() {
			return new FooController();
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

	@RestController
	public static class FooController {

		Span span;

		@RequestMapping(value = "/traceid", method = RequestMethod.GET)
		public String traceId(@RequestHeader("b3") String b3Single) {
			then(b3Single).isNotEmpty();
			return b3Single;
		}

		public Span getSpan() {
			return this.span;
		}

		public void clear() {
			this.span = null;
		}

	}

	// tag::client_parser_config[]
	@Configuration(proxyBeanMethods = false)
	public static class ClientParserConfiguration {

		// example for Feign
		@Bean(name = HttpClientRequestParser.NAME)
		HttpRequestParser myHttpClientRequestParser() {
			return (request, context, span) -> {
				// Span customization
				span.name(request.method());
				span.tag("ClientRequest", "Tag");
				Object unwrap = request.unwrap();
				if (unwrap instanceof feign.Request) {
					feign.Request req = (feign.Request) unwrap;
					// Span customization
					span.tag("ClientRequestFeign", req.httpMethod().name());
				}
			};
		}

		// example for Feign
		@Bean(name = HttpClientResponseParser.NAME)
		HttpResponseParser myHttpClientResponseParser() {
			return (response, context, span) -> {
				// Span customization
				span.tag("ClientResponse", "Tag");
				Object unwrap = response.unwrap();
				if (unwrap instanceof feign.Response) {
					feign.Response resp = (feign.Response) unwrap;
					// Span customization
					span.tag("ClientResponseFeign", String.valueOf(resp.status()));
				}
			};
		}

	}
	// end::client_parser_config[]

}
