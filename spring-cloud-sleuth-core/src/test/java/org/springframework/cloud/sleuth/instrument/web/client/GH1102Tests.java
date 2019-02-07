/*
 * Copyright 2013-2019 the original author or authors.
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

import brave.ScopedSpan;
import brave.Tracer;
import brave.sampler.Sampler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.BDDAssertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class GH1102Tests {

	@Autowired
	Tracer tracer;

	@Autowired
	WebClient webClient;

	@Autowired
	TestRetry testRetry;

	@Autowired
	ArrayListSpanReporter reporter;

	@LocalServerPort
	int port;

	@Test
	public void should_store_retries_as_separate_spans() throws Exception {
		ScopedSpan foo = this.tracer.startScopedSpan("foo");
		try {
			this.webClient.get().uri("http://localhost:" + this.port + "/test").retrieve()
					.bodyToMono(String.class).retry(1).block();
			BDDAssertions.fail("should throw exception");
		}
		catch (WebClientResponseException ex) {

		}
		finally {
			foo.finish();
		}

		BDDAssertions.then(this.testRetry.getHttpHeaders().get("x-b3-traceid"))
				.hasSize(1);
	}

	@EnableAutoConfiguration
	@Configuration
	static class WebConfig {

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		ArrayListSpanReporter reporter() {
			return new ArrayListSpanReporter();
		}

		@Bean
		WebClient webClient() {
			return WebClient.builder().build();
		}

		@Bean
		TestRetry testRetry() {
			return new TestRetry();
		}

	}

	@RestController
	static class TestRetry {

		private static final Log log = LogFactory.getLog(TestRetry.class);

		private MultiValueMap<String, String> httpHeaders;

		@GetMapping("test")
		Mono<String> test(@RequestHeader MultiValueMap<String, String> map) {
			this.httpHeaders = map;
			log.info("Processing test. Headers [" + this.httpHeaders + "]");
			return Mono.error(new RuntimeException("BOOM!"));
		}

		MultiValueMap<String, String> getHttpHeaders() {
			return this.httpHeaders;
		}

	}

}
