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

package org.springframework.cloud.sleuth.instrument.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import brave.Tracing;
import brave.sampler.Sampler;
import zipkin2.Span;
import org.assertj.core.api.BDDAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = TraceFilterWebIntegrationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.sleuth.http.legacy.enabled=true")
public class TraceFilterWebIntegrationTests {

	@Autowired Tracing tracer;
	@Autowired ArrayListSpanReporter accumulator;
	@Autowired Environment environment;
	@Rule public OutputCapture capture = new OutputCapture();

	@Before
	@After
	public void cleanup() {
		this.accumulator.clear();
	}

	@Test
	public void should_not_create_a_span_for_error_controller() {
		try {
			new RestTemplate().getForObject("http://localhost:" + port() + "/", String.class);
			BDDAssertions.fail("should fail due to runtime exception");
		} catch (Exception e) {
		}

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.accumulator.getSpans()).hasSize(1);
		Span reportedSpan = this.accumulator.getSpans().get(0);
		then(reportedSpan.tags())
				.containsEntry("http.status_code", "500")
				.containsEntry("error", "Request processing failed; nested exception is java.lang.RuntimeException: Throwing exception");
		// issue#714
		String hex = reportedSpan.traceId();
		String[] split = capture.toString().split("\n");
		List<String> list = Arrays.stream(split).filter(s -> s.contains(
				"Uncaught exception thrown"))
				.filter(s -> s.contains(hex + "," + hex + ",true]"))
				.collect(Collectors.toList());
		then(list).isNotEmpty();
	}

	@Test
	public void should_create_spans_for_endpoint_returning_unsuccessful_result() {
		try {
			new RestTemplate().getForObject("http://localhost:" + port() + "/test_bad_request", String.class);
			fail("should throw exception");
		} catch (HttpClientErrorException e) {
		}

		//TODO: Check if it should be 1 or 2 spans
		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.accumulator.getSpans()).hasSize(1);
		then(this.accumulator.getSpans().get(0).kind().ordinal()).isEqualTo(Span.Kind.SERVER.ordinal());
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}

	@EnableAutoConfiguration
	@Configuration
	public static class Config {

		@Bean ExceptionThrowingController controller() {
			return new ExceptionThrowingController();
		}

		@Bean ArrayListSpanReporter reporter() {
			return new ArrayListSpanReporter();
		}

		@Bean Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}


		@Bean RestTemplate restTemplate() {
			RestTemplate restTemplate = new RestTemplate();
			restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
				@Override public void handleError(ClientHttpResponse response)
						throws IOException {
				}
			});
			return restTemplate;
		}
	}

	@RestController
	public static class ExceptionThrowingController {

		@RequestMapping("/")
		public void throwException() {
			throw new RuntimeException("Throwing exception");
		}

		@RequestMapping(path = "/test_bad_request", method = RequestMethod.GET)
		public ResponseEntity<?> processFail() {
			return ResponseEntity.badRequest().build();
		}
	}
}
