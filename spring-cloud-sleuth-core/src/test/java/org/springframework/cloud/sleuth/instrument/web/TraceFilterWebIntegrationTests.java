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

package org.springframework.cloud.sleuth.instrument.web;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.ListOfSpans;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { TraceFilterWebIntegrationTests.Config.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TraceFilterWebIntegrationTests {

	@Autowired Tracer tracer;
	@Autowired ArrayListSpanAccumulator accumulator;
	@Autowired RestTemplate restTemplate;
	@Autowired Environment environment;

	@Before
	@After
	public void cleanup() {
		ExceptionUtils.setFail(true);
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_not_create_a_span_for_error_controller() {
		this.restTemplate.getForObject("http://localhost:" + port() + "/", String.class);

		then(this.tracer.getCurrentSpan()).isNull();
		then(new ListOfSpans(this.accumulator.getSpans()))
				.doesNotHaveASpanWithName("error")
				.hasASpanWithTagEqualTo("http.status_code", "500");
		then(ExceptionUtils.getLastException()).isNull();
		then(new ListOfSpans(this.accumulator.getSpans()))
				.hasASpanWithTagEqualTo(Span.SPAN_ERROR_TAG_NAME,
						"Request processing failed; nested exception is java.lang.RuntimeException: Throwing exception");
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

		@Bean ArrayListSpanAccumulator arrayListSpanAccumulator() {
			return new ArrayListSpanAccumulator();
		}

		@Bean Sampler alwaysSampler() {
			return new AlwaysSampler();
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
	}
}
